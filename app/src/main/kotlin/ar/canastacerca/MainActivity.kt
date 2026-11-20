package ar.canastacerca

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File

/**
 * Приложение — оболочка над сайтом Canasta Cerca: один WebView на весь экран.
 *
 * Корзину, адрес, способы оплаты и покупки сайт хранит сам (localStorage). Здесь только то, чего WebView не умеет
 * без помощи: строка состояния в цвет шапки сайта, Firu вместо белого экрана при запуске, «назад» закрывает панели
 * сайта и листает его историю, разрешения на камеру (сканер) и геолокацию, ссылки наружу (WhatsApp, карты, сайты сетей),
 * мост window.CanastaApp (docs/specs/mobile.md §3.2 в веб-проекте) и экран «сайт не открылся», куда можно вставить
 * новую ссылку на сервер.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var statusBg: View
    private lateinit var content: View
    private lateinit var loading: View
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorDetail: TextView
    private lateinit var serverInput: EditText

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** Адрес страницы, которая не открылась: пока она на экране, панель ошибки не прячем. */
    private var errorShownFor: String? = null
    private var clearHistoryOnLoad = false
    private var bridgeScript: ScriptHandler? = null

    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null
    private var pendingCamera: PermissionRequest? = null

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pendingGeo?.let { (origin, callback) -> callback.invoke(origin, result.values.any { it }, false) }
        pendingGeo = null
    }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCamera?.let { request ->
            if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
        }
        pendingCamera = null
    }

    /**
     * «Назад», как в Chrome на телефоне: сначала закрывает открытую панель сайта (адрес, оплата, сканер, покупка —
     * всё это <dialog>), потом листает историю сайта, а с первой страницы — выход.
     */
    private val back = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (errorPanel.visibility == View.VISIBLE || loading.visibility == View.VISIBLE) {
                historyBackOrLeave()
                return
            }
            web.evaluateJavascript(CLOSE_DIALOG) { closed -> if (closed != "true") historyBackOrLeave() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Строка состояния — в цвет шапки сайта (светлые значки), панель навигации — белая, как нижнее меню сайта
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        statusBg = findViewById(R.id.status_bg)
        content = findViewById(R.id.content)
        loading = findViewById(R.id.loading)
        progress = findViewById(R.id.progress)
        errorPanel = findViewById(R.id.error)
        errorDetail = findViewById(R.id.error_detail)
        serverInput = findViewById(R.id.server_input)

        // Сайт — между строкой состояния и панелью навигации, а с открытой клавиатурой — над ней
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            statusBg.updateLayoutParams { height = bars.top }
            content.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        setUpWebView()
        setUpErrorPanel()
        installBridge()
        onBackPressedDispatcher.addCallback(this, back)

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(startUrl())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        // В отладочной сборке сайт внутри приложения виден в Chrome на компьютере: chrome://inspect
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        // Ключ доступа (?k=…) сервер запоминает в cookie cc_k на 90 дней
        CookieManager.getInstance().setAcceptCookie(true)
        // Цвет фона сайта: без белой вспышки до первого кадра
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.canasta_background))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // корзина, адрес, способы оплаты, покупки — в localStorage
            mediaPlaybackRequiresUserGesture = false // видео с камеры в сканере штрихкода
            setGeolocationEnabled(true)
            // «Размер шрифта» из настроек телефона WebView умножает только на текст, а карточки и кнопки сайта заданы
            // в px: при крупном шрифте текст распирает блоки и на экран влезает мало. Сайт — в своём размере, как в
            // браузере; крупный шрифт для тех, кому он нужен, — со стороны сайта (W138)
            textZoom = 100
            // Чтобы сервер отличал приложение от браузера; сайт узнаёт себя в приложении по CanastaApp.info()
            userAgentString = "$userAgentString CanastaCercaAndroid/${BuildConfig.VERSION_NAME}"
        }
        web.webViewClient = SiteClient()
        web.webChromeClient = SiteChromeClient()
    }

    private fun setUpErrorPanel() {
        findViewById<Button>(R.id.retry).setOnClickListener {
            hideError()
            if (web.url.isNullOrEmpty()) web.loadUrl(startUrl()) else web.reload()
        }
        val open = findViewById<Button>(R.id.open_server)
        open.setOnClickListener { openServer(serverInput.text.toString().trim()) }
        serverInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                open.performClick()
                true
            } else {
                false
            }
        }
    }

    /** Новая ссылка на сервер (обычно из `npm run share`, с ?k=…): запоминаем и открываем. */
    private fun openServer(link: String) {
        val uri = Uri.parse(link)
        if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrEmpty()) {
            serverInput.error = getString(R.string.server_bad)
            return
        }
        prefs.edit()
            .putString(KEY_SERVER, link)
            .putString(KEY_SERVER_BUILD, buildFingerprint())
            .apply()
        clearHistoryOnLoad = true // «назад» не должен вести на старый сервер
        installBridge() // мост — только для нового адреса
        hideError()
        web.loadUrl(link)
    }

    /**
     * С чего начинать: ссылка, вставленная на экране ошибки (если сборка с тех пор не поменяла адрес),
     * иначе — из сборки: `canasta.baseUrl` и `canasta.accessKey` (ключ уходит как ?k=…).
     */
    private fun startUrl(): String {
        val saved = prefs.getString(KEY_SERVER, null)
        if (saved != null && prefs.getString(KEY_SERVER_BUILD, null) == buildFingerprint()) return saved
        val base = Uri.parse(BuildConfig.CANASTA_BASE_URL)
        val key = BuildConfig.CANASTA_ACCESS_KEY
        return if (key.isBlank()) base.toString() else base.buildUpon().appendQueryParameter("k", key).build().toString()
    }

    private fun buildFingerprint(): String = BuildConfig.CANASTA_BASE_URL + "|" + BuildConfig.CANASTA_ACCESS_KEY

    private fun siteHost(): String? = Uri.parse(startUrl()).host

    /** «https://canastacerca.com» — для правил моста: он появляется только на страницах этого адреса. */
    private fun siteOrigin(): String {
        val uri = Uri.parse(startUrl())
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun historyBackOrLeave() {
        if (web.canGoBack()) {
            hideError()
            web.goBack()
        } else {
            back.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            back.isEnabled = true
        }
    }

    private fun showError(url: String, detail: String?) {
        errorShownFor = url
        val server = Uri.parse(startUrl()).buildUpon().clearQuery().build() // ключ на экран не выводим
        errorDetail.text = getString(R.string.error_detail, server.toString(), detail.orEmpty())
        progress.visibility = View.GONE
        loading.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorShownFor = null
        errorPanel.visibility = View.GONE
    }

    /* ---------- Мост window.CanastaApp (docs/specs/mobile.md §3.2) ---------- */

    /**
     * window.CanastaApp — только на страницах нашего сайта (правило по адресу; на чужих страницах моста нет).
     * Пока есть info() и share(). pushAsk, scan и openSettings появятся со своими версиями: без них сайт делает
     * всё как в браузере. Старый WebView без нужных возможностей — тоже как в браузере.
     */
    private fun installBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            return
        }
        val rules = setOf(siteOrigin())
        bridgeScript?.let {
            it.remove()
            WebViewCompat.removeWebMessageListener(web, NATIVE)
        }
        WebViewCompat.addWebMessageListener(web, NATIVE, rules) { _, message, _, isMainFrame, reply ->
            if (isMainFrame) onBridgeMessage(message.data, reply)
        }
        bridgeScript = WebViewCompat.addDocumentStartJavaScript(web, bridgeJs(), rules)
    }

    /** Ответ сайту — событием «canastaapp» на window: {call, ok} (public/app.js → appCall). */
    private fun onBridgeMessage(raw: String?, reply: JavaScriptReplyProxy) {
        val msg = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return
        val call = msg.optString("call")
        val ok = when (call) {
            "share" -> share(msg.optJSONObject("arg") ?: JSONObject())
            else -> false
        }
        reply.postMessage(JSONObject().put("call", call).put("ok", ok).toString())
    }

    /** Системное меню «Поделиться»: CanastaApp.share({text, url, image}), image — data:image/…;base64,… */
    private fun share(arg: JSONObject): Boolean {
        val text = arg.optString("text").trim()
        val url = arg.optString("url").trim()
        val body = when {
            url.isEmpty() || text.contains(url) -> text
            text.isEmpty() -> url
            else -> "$text $url"
        }
        val send = Intent(Intent.ACTION_SEND)
        val image = saveSharedImage(arg.optString("image"))
        if (image != null) {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", image.first)
            send.type = image.second
            send.putExtra(Intent.EXTRA_STREAM, uri)
            send.clipData = ClipData.newRawUri(null, uri) // картинка видна в самом меню
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            if (body.isEmpty()) return false
            send.type = "text/plain"
        }
        if (body.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, body)
        return try {
            startActivity(Intent.createChooser(send, null))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Картинку из data: URL — в файл в кэше (его и отдаём через FileProvider). Старые файлы чистим. */
    private fun saveSharedImage(dataUrl: String): Pair<File, String>? {
        val match = DATA_URL.matchEntire(dataUrl) ?: return null
        val mime = match.groupValues[1]
        val bytes = runCatching { Base64.decode(match.groupValues[2], Base64.DEFAULT) }.getOrNull() ?: return null
        val dir = File(cacheDir, "share").apply { mkdirs() }
        val old = System.currentTimeMillis() - 10 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
        val ext = if (mime == "image/jpeg") "jpg" else mime.substringAfter('/')
        val file = File(dir, "canasta-${System.currentTimeMillis()}.$ext")
        return runCatching { file.writeBytes(bytes) }.map { file to mime }.getOrNull()
    }

    /** То, что видит сайт: window.CanastaApp с info() и share(). Ответы — событием «canastaapp». */
    private fun bridgeJs(): String {
        val info = JSONObject()
            .put("platform", "android")
            .put("version", "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}")
            .put("push", "off")
            .toString()
        return """
            (function () {
              if (window.CanastaApp) return;
              var INFO = ${JSONObject.quote(info)};
              var hooked = false;
              function nativeBridge() {
                var n = window.$NATIVE;
                if (n && !hooked) {
                  hooked = true;
                  n.onmessage = function (e) {
                    try { window.dispatchEvent(new CustomEvent('canastaapp', { detail: e.data })); } catch (err) {}
                  };
                }
                return n;
              }
              nativeBridge();
              window.CanastaApp = {
                info: function () { return INFO; },
                share: function (data) {
                  var n = nativeBridge();
                  if (!n) throw new Error('CanastaApp');
                  n.postMessage(JSON.stringify({ call: 'share', arg: data || {} }));
                }
              };
            })();
        """.trimIndent()
    }

    /* ---------- Ссылки наружу и разрешения ---------- */

    /** WhatsApp, «Cómo llegar», сайты сетей и прочее чужое — в своих приложениях, а не внутри нашего. */
    private fun openOutside(uri: Uri) {
        val intent = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()?.apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        try {
            if (intent == null) throw ActivityNotFoundException()
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = intent?.getStringExtra("browser_fallback_url")
            if (fallback != null && !fallback.startsWith("intent:")) {
                openOutside(Uri.parse(fallback))
            } else {
                Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private inner class SiteClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when {
                !request.isForMainFrame -> false
                uri.scheme.orEmpty() in INTERNAL_SCHEMES -> false
                (uri.scheme == "http" || uri.scheme == "https") && uri.host == siteHost() -> false
                else -> {
                    openOutside(uri)
                    true
                }
            }
        }

        // Первый кадр сайта нарисован — убираем Firu
        override fun onPageCommitVisible(view: WebView, url: String?) {
            loading.visibility = View.GONE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(OPEN_IN_PLACE, null)
            val ok = url != errorShownFor
            if (ok) {
                hideError()
                loading.visibility = View.GONE
            }
            if (ok && clearHistoryOnLoad) {
                view.clearHistory()
                clearHistoryOnLoad = false
            }
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) showError(request.url.toString(), error.description?.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            // 401 — ссылка без ключа или ключ устарел; 5xx — сервер или туннель не отвечает. Свою 404 сайт рисует сам
            val code = response.statusCode
            if (request.isForMainFrame && (code == 401 || code >= 500)) showError(request.url.toString(), "HTTP $code")
        }
    }

    private inner class SiteChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress < 100 && errorShownFor == null) View.VISIBLE else View.GONE
        }

        // «Mi ubicación» на сайте. Браузерная геолокация работает только по https (и на localhost)
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (LOCATION.any { granted(it) }) {
                callback.invoke(origin, true, false)
            } else {
                pendingGeo = origin to callback
                askLocation.launch(LOCATION)
            }
        }

        // Камера — только сканеру штрихкода нашего сайта (тоже только по https или на localhost)
        override fun onPermissionRequest(request: PermissionRequest) {
            val camera = PermissionRequest.RESOURCE_VIDEO_CAPTURE
            if (request.origin.host != siteHost() || camera !in request.resources) {
                request.deny()
                return
            }
            if (granted(Manifest.permission.CAMERA)) {
                request.grant(arrayOf(camera))
            } else {
                pendingCamera = request
                askCamera.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingCamera === request) pendingCamera = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "canasta"
        const val KEY_SERVER = "server_link"
        const val KEY_SERVER_BUILD = "server_link_build"
        val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        /** Имя объекта, через который мост говорит с приложением (WebMessageListener). Сайт его не трогает. */
        const val NATIVE = "CanastaNative"

        val DATA_URL = Regex("^data:(image/(?:png|jpeg|webp));base64,(.+)$", RegexOption.DOT_MATCHES_ALL)

        /** Эти адреса WebView открывает сам: служебные страницы и картинки, которые сайт рисует в браузере. */
        val INTERNAL_SCHEMES = setOf("about", "blob", "data", "javascript")

        /**
         * Сайт открывает WhatsApp через window.open(…, '_blank'). В WebView нового окна нет — переходим по ссылке
         * на месте, а shouldOverrideUrlLoading отправляет её в WhatsApp.
         */
        const val OPEN_IN_PLACE =
            "if(!window.__ccOpen){window.__ccOpen=1;window.open=function(u){if(u)location.href=u;return null;};}"

        /** Закрыть верхнюю открытую панель сайта, как Esc: событие cancel (сайт может отказать), потом close(). */
        const val CLOSE_DIALOG =
            "(function(){var d=document.querySelectorAll('dialog[open]');if(!d.length)return false;" +
                "var t=d[d.length-1],e=new Event('cancel',{cancelable:true});if(t.dispatchEvent(e))t.close();return true;})()"
    }
}
