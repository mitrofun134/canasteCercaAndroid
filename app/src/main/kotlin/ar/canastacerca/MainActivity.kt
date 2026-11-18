package ar.canastacerca

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Приложение — оболочка над сайтом Canasta Cerca: один WebView на весь экран.
 *
 * Корзину, адрес, способы оплаты и покупки сайт хранит сам (localStorage). Здесь только то, чего WebView
 * не умеет без помощи: разрешения на камеру (сканер штрихкода) и геолокацию, ссылки наружу (WhatsApp, карты,
 * сайты сетей), «назад» по истории сайта и экран «сайт не открылся», где можно вставить новую ссылку на сервер:
 * адрес из `npm run share` меняется при каждом запуске.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorDetail: TextView
    private lateinit var serverInput: EditText

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** Адрес страницы, которая не открылась: пока она на экране, панель ошибки не прячем. */
    private var errorShownFor: String? = null
    private var clearHistoryOnLoad = false

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

    /** «Назад» листает историю сайта; когда листать некуда, кнопка выключена и работает как обычно (выход). */
    private val backInSite = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!web.canGoBack()) return
            hideError()
            web.goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Сайт светлый: тёмные значки в строке состояния и навигации при любой теме телефона
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        errorPanel = findViewById(R.id.error)
        errorDetail = findViewById(R.id.error_detail)
        serverInput = findViewById(R.id.server_input)

        // Сайт — между строкой состояния и панелью навигации, а с открытой клавиатурой — над ней
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        setUpWebView()
        setUpErrorPanel()
        onBackPressedDispatcher.addCallback(this, backInSite)

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
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // корзина, адрес, способы оплаты, покупки — в localStorage
            mediaPlaybackRequiresUserGesture = false // видео с камеры в сканере штрихкода
            setGeolocationEnabled(true)
            // Чтобы сервер и аналитика отличали приложение от браузера
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

    private fun showError(url: String, detail: String?) {
        errorShownFor = url
        val server = Uri.parse(startUrl()).buildUpon().clearQuery().build() // ключ на экран не выводим
        errorDetail.text = getString(R.string.error_detail, server.toString(), detail.orEmpty())
        progress.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorShownFor = null
        errorPanel.visibility = View.GONE
    }

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

        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(OPEN_IN_PLACE, null)
            val ok = url != errorShownFor
            if (ok) hideError()
            if (ok && clearHistoryOnLoad) {
                view.clearHistory()
                clearHistoryOnLoad = false
            }
            backInSite.isEnabled = view.canGoBack()
            CookieManager.getInstance().flush()
        }

        // Сайт — одна страница: переходы между разделами идут через history.pushState
        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            backInSite.isEnabled = view.canGoBack()
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

        /** Эти адреса WebView открывает сам: служебные страницы и картинки, которые сайт рисует в браузере. */
        val INTERNAL_SCHEMES = setOf("about", "blob", "data", "javascript")

        /**
         * Сайт открывает WhatsApp через window.open(…, '_blank') («Compartir»). В WebView нового окна нет —
         * переходим по ссылке на месте, а shouldOverrideUrlLoading отправляет её в WhatsApp.
         */
        const val OPEN_IN_PLACE =
            "if(!window.__ccOpen){window.__ccOpen=1;window.open=function(u){if(u)location.href=u;return null;};}"
    }
}
