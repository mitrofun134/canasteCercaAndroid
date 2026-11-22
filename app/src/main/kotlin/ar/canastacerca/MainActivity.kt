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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Приложение — оболочка над сайтом Canasta Cerca: один WebView на весь экран.
 *
 * Корзину, адрес, способы оплаты и покупки сайт хранит сам (localStorage). Здесь только то, чего WebView не умеет
 * без помощи: строка состояния в цвет шапки сайта, Firu вместо белого экрана при запуске, «назад» закрывает панели
 * сайта и листает его историю, «потянуть вниз — обновить», разрешения на камеру (сканер) и геолокацию — только нашему
 * сайту, ссылки наружу (WhatsApp, карты, сайты сетей), наши ссылки canastacerca.com — сразу здесь (App Links),
 * мост window.CanastaApp (docs/specs/mobile.md §3.2 в веб-проекте), флаги и обязательное обновление из /api/config
 * (§3.6) и экран «сайт не открылся». Поле для другой ссылки на сервер на этом экране — только в отладочной сборке.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var pull: SitePull
    private lateinit var statusBg: View
    private lateinit var content: View
    private lateinit var loading: View
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorDetail: TextView
    private lateinit var serverInput: EditText
    private lateinit var updatePanel: View

    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    /** Адрес страницы, которая не открылась: пока она на экране, панель ошибки не прячем. */
    private var errorShownFor: String? = null
    private var clearHistoryOnLoad = false
    private var bridgeScript: ScriptHandler? = null

    /* Обновление сайта (docs/specs/mobile.md §3.1): каждые CHECK_EVERY_MS, при возврате в приложение и после загрузки
       страницы сверяем версию страницы с сервером; вышла новая — перезагружаем, когда человек ничего не делает. */
    private val main = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var pageReady = false
    private var reloadPending = false
    /** Версия сервера, ради которой уже перезагружали: второй раз ради неё не перезагружаем (защита от круга). */
    private var updatedFor: String? = null
    private val periodicCheck: Runnable = object : Runnable {
        override fun run() {
            checkSiteVersion()
            main.postDelayed(this, CHECK_EVERY_MS)
        }
    }
    private val retryReload = Runnable { reloadWhenSafe() }

    /** Процесс сайта (renderer WebView) умер: этот WebView больше трогать нельзя, экран пересоздаётся. */
    private var webGone = false

    /** Раздел app из /api/config (§3.6), последний полученный: флаги и minVersion действуют и без сети. */
    private var appConfig = JSONObject()

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
            if (updatePanel.visibility == View.VISIBLE) {
                leave()
                return
            }
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
        pull = findViewById(R.id.pull)
        statusBg = findViewById(R.id.status_bg)
        content = findViewById(R.id.content)
        loading = findViewById(R.id.loading)
        progress = findViewById(R.id.progress)
        errorPanel = findViewById(R.id.error)
        errorDetail = findViewById(R.id.error_detail)
        serverInput = findViewById(R.id.server_input)
        updatePanel = findViewById(R.id.update)

        // Сайт — между строкой состояния и панелью навигации, а с открытой клавиатурой — над ней
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            statusBg.updateLayoutParams { height = bars.top }
            content.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // В релизе адрес сайта — только из сборки: ссылку, вставленную в отладочной сборке, забываем
        if (!BuildConfig.DEBUG) prefs.edit().remove(KEY_SERVER).remove(KEY_SERVER_BUILD).apply()
        appConfig = runCatching { JSONObject(prefs.getString(KEY_APP_CONFIG, null) ?: "{}") }.getOrDefault(JSONObject())

        setUpWebView()
        setUpPull()
        setUpErrorPanel()
        setUpUpdatePanel()
        installBridge()
        onBackPressedDispatcher.addCallback(this, back)
        applyAppConfig()
        fetchAppConfig()

        val link = linkFrom(intent)
        when {
            savedInstanceState == null && link != null -> web.loadUrl(link)
            savedInstanceState == null || web.restoreState(savedInstanceState) == null -> web.loadUrl(startUrl())
        }
    }

    /** Наша ссылка, пока приложение уже открыто (из WhatsApp, из пуша): открываем её здесь же. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkFrom(intent)?.let {
            hideError()
            web.loadUrl(it)
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

    /**
     * «Потянуть вниз — обновить» (T3): только когда страница в самом верху, нет открытой панели и палец не в карусели,
     * не на карте, не в поле и не в области, которая листается сама. Это решает сайт (PULL_OK) в момент касания.
     */
    private fun setUpPull() {
        pull.setColorSchemeResources(R.color.canasta_celeste)
        pull.setProgressBackgroundColorSchemeResource(R.color.canasta_card)
        pull.onTouchDown = { x, y, gesture ->
            web.evaluateJavascript("$PULL_OK($x,$y)") { answer -> pull.answer(gesture, answer == "true") }
        }
        pull.setOnRefreshListener {
            when {
                web.url.isNullOrEmpty() -> web.loadUrl(startUrl())
                errorPanel.visibility == View.VISIBLE -> {
                    hideError()
                    web.reload()
                }
                else -> web.evaluateJavascript(FRESH_RELOAD, null)
            }
        }
    }

    private fun setUpErrorPanel() {
        findViewById<Button>(R.id.retry).setOnClickListener {
            hideError()
            if (web.url.isNullOrEmpty()) web.loadUrl(startUrl()) else web.reload()
        }
        val open = findViewById<Button>(R.id.open_server)
        val hint = findViewById<TextView>(R.id.error_hint)
        // Поле для другой ссылки на сервер (туннель npm run share) — только в отладочной сборке: в релизе это была бы
        // адресная строка, и чужой адрес получил бы камеру и местоположение (T4). В разметке оно скрыто
        if (!BuildConfig.DEBUG) {
            hint.setText(R.string.error_hint)
            return
        }
        hint.setText(R.string.error_hint_debug)
        for (id in intArrayOf(R.id.server_label, R.id.server_input, R.id.open_server)) {
            findViewById<View>(id).visibility = View.VISIBLE
        }
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

    /** Отладочная сборка: новая ссылка на сервер (обычно из `npm run share`, с ?k=…) — запоминаем и открываем. */
    private fun openServer(link: String) {
        if (!BuildConfig.DEBUG) return
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
     * С чего начинать: из сборки — `canasta.baseUrl` и `canasta.accessKey` (ключ уходит как ?k=…). В отладочной сборке —
     * ссылка, вставленная на экране ошибки, если сборка с тех пор не поменяла адрес.
     */
    private fun startUrl(): String {
        if (BuildConfig.DEBUG) {
            val saved = prefs.getString(KEY_SERVER, null)
            if (saved != null && prefs.getString(KEY_SERVER_BUILD, null) == buildFingerprint()) return saved
        }
        val base = Uri.parse(BuildConfig.CANASTA_BASE_URL)
        val key = BuildConfig.CANASTA_ACCESS_KEY
        return if (key.isBlank()) base.toString() else base.buildUpon().appendQueryParameter("k", key).build().toString()
    }

    private fun buildFingerprint(): String = BuildConfig.CANASTA_BASE_URL + "|" + BuildConfig.CANASTA_ACCESS_KEY

    /**
     * Наш сайт: в релизе — только адрес из сборки (`BuildConfig.CANASTA_BASE_URL`), ему мост, камера и местоположение.
     * В отладочной — ещё и ссылка, вставленная на экране ошибки.
     */
    private fun siteBase(): Uri = Uri.parse(if (BuildConfig.DEBUG) startUrl() else BuildConfig.CANASTA_BASE_URL)

    private fun siteHost(): String? = siteBase().host

    /** «https://canastacerca.com» — для правил моста: он появляется только на страницах этого адреса. */
    private fun siteOrigin(): String {
        val uri = siteBase()
        val port = if (uri.port != -1) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    /** Страница или запрос разрешения — от нашего сайта: та же схема, хост и порт. */
    private fun isSite(uri: Uri?): Boolean {
        if (uri == null) return false
        val site = siteBase()
        return uri.scheme == site.scheme && uri.host.equals(site.host, ignoreCase = true) && portOf(uri) == portOf(site)
    }

    private fun portOf(uri: Uri): Int = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80

    /**
     * Ссылка canastacerca.com, которой нас открыли (App Links, §3.5), — на адрес сайта из сборки. Ключ добавляем, если
     * cookie с ним ещё нет: человек мог поставить приложение и впервые открыть его ссылкой из WhatsApp.
     */
    private fun linkFrom(intent: Intent?): String? {
        val view = intent ?: return null
        if (view.action != Intent.ACTION_VIEW) return null
        val uri = view.data ?: return null
        if (uri.scheme != "https" || !uri.host.equals(LINK_HOST, ignoreCase = true)) return null
        val site = siteBase()
        val link = uri.buildUpon().scheme(site.scheme).encodedAuthority(site.encodedAuthority)
        val key = BuildConfig.CANASTA_ACCESS_KEY
        val hasCookie = CookieManager.getInstance().getCookie(siteOrigin())?.contains("cc_k=") == true
        if (key.isNotBlank() && !hasCookie && uri.getQueryParameter("k") == null) link.appendQueryParameter("k", key)
        return link.build().toString()
    }

    private fun historyBackOrLeave() {
        if (web.canGoBack()) {
            hideError()
            web.goBack()
        } else {
            leave()
        }
    }

    private fun leave() {
        back.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        back.isEnabled = true
    }

    private fun showError(url: String, detail: String?) {
        errorShownFor = url
        // В релизе — только код ошибки, без адреса сервера; ключ на экран не выводим никогда
        errorDetail.text = if (BuildConfig.DEBUG) {
            val server = Uri.parse(startUrl()).buildUpon().clearQuery().build()
            getString(R.string.error_detail, server.toString(), detail.orEmpty())
        } else {
            detail.orEmpty()
        }
        progress.visibility = View.GONE
        loading.visibility = View.GONE
        pull.isRefreshing = false
        errorPanel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorShownFor = null
        errorPanel.visibility = View.GONE
    }

    /* ---------- Флаги и обязательное обновление (docs/specs/mobile.md §3.6) ---------- */

    /**
     * GET /api/config при запуске, в фоне. Берём только раздел app и запоминаем: следующий запуск применит его сразу,
     * даже без сети. Нет раздела или сети — всё как раньше.
     */
    private fun fetchAppConfig() {
        val url = siteOrigin() + "/api/config"
        val key = BuildConfig.CANASTA_ACCESS_KEY
        Thread {
            val app = runCatching {
                val http = URL(url).openConnection() as HttpURLConnection
                try {
                    http.connectTimeout = 5000
                    http.readTimeout = 5000
                    http.setRequestProperty("Accept", "application/json")
                    http.setRequestProperty("X-CC-Client", "android/${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}")
                    if (key.isNotBlank()) http.setRequestProperty("Cookie", "cc_k=$key")
                    if (http.responseCode != 200) return@runCatching null
                    JSONObject(http.inputStream.bufferedReader().use { it.readText() }).optJSONObject("app")
                } finally {
                    http.disconnect()
                }
            }.getOrNull() ?: return@Thread
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val changed = app.toString() != appConfig.toString()
                appConfig = app
                prefs.edit().putString(KEY_APP_CONFIG, app.toString()).apply()
                if (changed) {
                    applyAppConfig()
                    installBridge() // флаги моста — со следующей страницы
                }
            }
        }.start()
    }

    /* ---------- Обновление сайта в приложении ---------- */

    /**
     * Та же страница с сервера (мимо кэша сайта и WebView) против той, что на экране: у сервера есть файл с версией
     * (?v=…), которого на странице нет, — сайт выложен заново, перезагружаем. Ключ — cookie cc_k, как в /api/config.
     */
    private fun checkSiteVersion() {
        if (webGone) return
        val current = web.url?.let { Uri.parse(it) } ?: return
        if (!pageReady || !isSite(current) || errorPanel.visibility == View.VISIBLE) return
        lastCheck = SystemClock.elapsedRealtime()
        val url = siteOrigin() + (current.encodedPath ?: "/")
        val key = BuildConfig.CANASTA_ACCESS_KEY
        Thread {
            val server = runCatching {
                val http = URL(url).openConnection() as HttpURLConnection
                try {
                    http.connectTimeout = 8000
                    http.readTimeout = 8000
                    http.useCaches = false
                    http.setRequestProperty("Accept", "text/html")
                    if (key.isNotBlank()) http.setRequestProperty("Cookie", "cc_k=$key")
                    if (http.responseCode != 200) return@runCatching null
                    val html = http.inputStream.bufferedReader().use { it.readText() }
                    SITE_ASSET.findAll(html).map { it.groupValues[1] }.toSortedSet()
                } finally {
                    http.disconnect()
                }
            }.getOrNull()
            if (server.isNullOrEmpty()) return@Thread
            runOnUiThread {
                if (isFinishing || isDestroyed || webGone) return@runOnUiThread
                web.evaluateJavascript(PAGE_ASSETS) { raw ->
                    val list = runCatching { JSONArray(raw) }.getOrNull() ?: return@evaluateJavascript
                    val page = (0 until list.length()).map { list.optString(it) }.toSet()
                    val version = server.joinToString(",")
                    if (page.isEmpty() || page.containsAll(server) || version == updatedFor) return@evaluateJavascript
                    updatedFor = version
                    reloadPending = true
                    reloadWhenSafe()
                }
            }
        }.start()
    }

    /** Перезагрузка, когда человек ничего не делает: нет открытой панели и курсора в поле; иначе — через минуту. */
    private fun reloadWhenSafe() {
        main.removeCallbacks(retryReload)
        if (webGone) return
        web.evaluateJavascript(CAN_RELOAD) { ok ->
            if (ok == "true") {
                reloadPending = false
                web.evaluateJavascript(FRESH_RELOAD, null)
            } else {
                main.postDelayed(retryReload, 60_000L)
            }
        }
    }

    /** Версия ниже minVersion.android — экран «Actualizá la app» поверх всего. */
    private fun applyAppConfig() {
        val min = appConfig.optJSONObject("minVersion")?.opt("android")
        updatePanel.visibility = if (belowMin(min)) View.VISIBLE else View.GONE
    }

    /**
     * minVersion.android — число `versionCode` (0.3.2 — это 5); строка из цифр — тоже `versionCode`; строка вида
     * «0.4.0» — сравниваем с `versionName` по частям. Непонятное значение — обновление не требуем.
     */
    private fun belowMin(min: Any?): Boolean = when (min) {
        is Number -> BuildConfig.VERSION_CODE < min.toInt()
        is String -> {
            val s = min.trim()
            when {
                s.matches(Regex("\\d+")) -> BuildConfig.VERSION_CODE < s.toInt()
                s.matches(Regex("\\d+(\\.\\d+)*")) -> compareVersions(BuildConfig.VERSION_NAME, s) < 0
                else -> false
            }
        }
        else -> false
    }

    private fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    /** Флаг из app.flags: выключен только явным false (нет раздела или флага — включено). */
    private fun flagOn(name: String): Boolean = appConfig.optJSONObject("flags")?.opt(name) != false

    private fun setUpUpdatePanel() {
        findViewById<Button>(R.id.update_button).setOnClickListener {
            val market = Uri.parse("market://details?id=$packageName")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, market))
            } catch (e: ActivityNotFoundException) {
                openOutside(Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            }
        }
    }

    /* ---------- Мост window.CanastaApp (docs/specs/mobile.md §3.2) ---------- */

    /**
     * window.CanastaApp — только на страницах нашего сайта (правило по адресу; на чужих страницах моста нет).
     * Есть info(), share() (если флаг share не выключен) и haptic(). pushAsk, scan и openSettings появятся со своими
     * версиями: без них сайт делает всё как в браузере. Старый WebView без нужных возможностей — тоже как в браузере.
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

    /** Ответ сайту — событием «canastaapp» на window: {call, ok} (public/app.js → appCall). haptic — без ответа. */
    private fun onBridgeMessage(raw: String?, reply: JavaScriptReplyProxy) {
        val msg = runCatching { JSONObject(raw.orEmpty()) }.getOrNull() ?: return
        val call = msg.optString("call")
        if (call == "haptic") {
            web.post { haptic() }
            return
        }
        val ok = when (call) {
            "share" -> flagOn("share") && share(msg.optJSONObject("arg") ?: JSONObject())
            else -> false
        }
        reply.postMessage(JSONObject().put("call", call).put("ok", ok).toString())
    }

    /**
     * Лёгкий отклик при «Agregar» (T3): системная вибрация касания, как у клавиатуры. Разрешения не нужно; если в
     * телефоне «Вибрация при касании» выключена — ничего.
     */
    private fun haptic() {
        web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Ошибка без падения — в Crashlytics, если он есть в сборке (app/google-services.json); иначе ничего. */
    private fun reportNonFatal(e: Throwable) {
        if (!BuildConfig.CRASHLYTICS) return
        runCatching {
            val crashlytics = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val instance = crashlytics.getMethod("getInstance").invoke(null)
            crashlytics.getMethod("recordException", Throwable::class.java).invoke(instance, e)
        }
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

    /** То, что видит сайт: window.CanastaApp с info(), share() и haptic(). Ответы — событием «canastaapp». */
    private fun bridgeJs(): String {
        val info = JSONObject()
            .put("platform", "android")
            .put("version", "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}")
            .put("push", "off")
            .toString()
        val shareOn = flagOn("share")
        return """
            (function () {
              if (window.CanastaApp) return;
              var INFO = ${JSONObject.quote(info)};
              var SHARE = $shareOn;
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
              var app = {
                info: function () { return INFO; },
                haptic: function (kind) {
                  var n = nativeBridge();
                  if (n) n.postMessage(JSON.stringify({ call: 'haptic', arg: { kind: String(kind || 'tap') } }));
                }
              };
              if (SHARE) {
                app.share = function (data) {
                  var n = nativeBridge();
                  if (!n) throw new Error('CanastaApp');
                  n.postMessage(JSON.stringify({ call: 'share', arg: data || {} }));
                };
              }
              window.CanastaApp = app;
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
                (uri.scheme == "http" || uri.scheme == "https") && uri.host.equals(siteHost(), ignoreCase = true) -> false
                else -> {
                    openOutside(uri)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            pageReady = false
        }

        // Первый кадр сайта нарисован — убираем Firu
        override fun onPageCommitVisible(view: WebView, url: String?) {
            loading.visibility = View.GONE
            pull.isRefreshing = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            view.evaluateJavascript(OPEN_IN_PLACE, null)
            pull.isRefreshing = false
            val ok = url != errorShownFor
            if (ok) {
                hideError()
                loading.visibility = View.GONE
            }
            // Страница могла прийти из кэша сайта (service worker) — сверим версию с сервером, но не чаще раза в минуту
            pageReady = ok
            if (ok && SystemClock.elapsedRealtime() - lastCheck > 60_000L) main.postDelayed({ checkSiteVersion() }, 2000L)
            if (ok && clearHistoryOnLoad) {
                view.clearHistory()
                clearHistoryOnLoad = false
            }
            CookieManager.getInstance().flush()
        }

        /**
         * Процесс, который рисует сайт, упал или его выгрузил Android (мало памяти — бывает на старых телефонах). Без этого
         * метода падает всё приложение. Сообщаем в Crashlytics и открываем экран заново: корзина, адрес и оплата — в
         * localStorage сайта. Второй раз подряд за 10 секунд — закрываемся, чтобы не крутиться по кругу.
         */
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            webGone = true
            main.removeCallbacksAndMessages(null)
            val why = if (detail.didCrash()) "упал" else "выгружен системой"
            reportNonFatal(IllegalStateException("WebView: процесс сайта $why"))
            val now = SystemClock.elapsedRealtime()
            if (now - lastRendererGone < 10_000L) finish() else recreate()
            lastRendererGone = now
            return true
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
            // Пока крутится круг «потянуть — обновить», полоска сверху не нужна
            progress.visibility =
                if (newProgress < 100 && errorShownFor == null && !pull.isRefreshing) View.VISIBLE else View.GONE
        }

        // «Mi ubicación» — только нашему сайту. Браузерная геолокация работает только по https (и на localhost)
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (!isSite(Uri.parse(origin))) {
                callback.invoke(origin, false, false)
                return
            }
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
            if (!isSite(request.origin) || camera !in request.resources) {
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
        if (!webGone) web.saveState(outState)
    }

    /**
     * Android держит приложение в памяти сутками, и значок возвращает ту же страницу. Поэтому при возврате (если
     * с прошлой сверки прошло больше RESUME_CHECK_MS) и раз в CHECK_EVERY_MS, пока приложение открыто, сверяем версию
     * сайта; отложенную перезагрузку — делаем сейчас.
     */
    override fun onResume() {
        super.onResume()
        if (webGone) return
        web.onResume()
        if (reloadPending) reloadWhenSafe()
        else if (SystemClock.elapsedRealtime() - lastCheck > RESUME_CHECK_MS) checkSiteVersion()
        main.removeCallbacks(periodicCheck)
        main.postDelayed(periodicCheck, CHECK_EVERY_MS)
    }

    override fun onPause() {
        main.removeCallbacks(periodicCheck)
        main.removeCallbacks(retryReload)
        if (!webGone) web.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "canasta"
        const val KEY_SERVER = "server_link"
        const val KEY_SERVER_BUILD = "server_link_build"
        const val KEY_APP_CONFIG = "app_config"

        /** Когда в прошлый раз умер процесс сайта (onRenderProcessGone): второй раз подряд — не пересоздаём экран. */
        var lastRendererGone = 0L

        /** Как часто, пока приложение открыто, сверяем версию сайта с сервером (ГД 22.11: раз в полчаса). */
        const val CHECK_EVERY_MS = 30 * 60 * 1000L

        /** При возврате в приложение сверяем, если с прошлой сверки прошло больше этого. */
        const val RESUME_CHECK_MS = 5 * 60 * 1000L

        /** Файлы сайта с версией в адресе — по ним видно, что сайт выложен заново (lib/web.js → renderPage). */
        val SITE_ASSET = Regex("(?:src|href)=\"(/[^\"?#]+\\.(?:js|css)\\?v=[^\"&#]+)\"")
        val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        /** Наши ссылки, которые открываются сразу в приложении (App Links): тот же хост — в AndroidManifest.xml. */
        const val LINK_HOST = "canastacerca.com"

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

        /** Файлы с версией (?v=…), которые загрузила страница на экране: скрипты и стили, в том числе preload. */
        const val PAGE_ASSETS =
            "[].map.call(document.querySelectorAll('[src*=\"?v=\"],[href*=\"?v=\"]')," +
                "function(e){return e.getAttribute('src')||e.getAttribute('href');})"

        /** Можно ли перезагрузить сейчас: нет открытой панели (<dialog>) и курсора в поле ввода. */
        const val CAN_RELOAD =
            "(function(){var a=document.activeElement,t=a&&a.tagName;return !document.querySelector('dialog[open]')&&" +
                "!(t==='INPUT'||t==='TEXTAREA'||t==='SELECT'||(a&&a.isContentEditable));})()"

        /**
         * Перезагрузить страницу свежей: сначала убрать из кэша сайта (Cache Storage service worker'а) сохранённые
         * страницы — адреса нашего сайта без ?… и без расширения, кроме /api/ — иначе он может снова показать старую.
         * Файлы с версией, картинки и данные остаются.
         */
        const val FRESH_RELOAD =
            "(function(){var o=location.origin,go=function(){location.reload();};" +
                "if(!window.caches){go();return;}" +
                "caches.keys().then(function(ns){return Promise.all(ns.map(function(n){return caches.open(n).then(function(c){" +
                "return c.keys().then(function(rs){return Promise.all(rs.filter(function(r){var u=new URL(r.url);" +
                "return u.origin===o&&!u.search&&u.pathname.indexOf('/api/')!==0&&!/\\.[A-Za-z0-9]+$/.test(u.pathname);})" +
                ".map(function(r){return c.delete(r);}));});});}));}).then(go,go);})()"

        /**
         * Можно ли «потянуть — обновить» из точки касания (x, y в CSS px): страница в самом верху, нет открытой панели
         * (<dialog>) и прокрутка страницы не заперта (лист поверх), а под пальцем нет карусели, карты, поля ввода,
         * своей прокрутки и touch-action без вертикали.
         */
        const val PULL_OK =
            "(function(x,y){try{var d=document,se=d.scrollingElement||d.documentElement,b=d.body;" +
                "if(!b||window.scrollY>0||(se&&se.scrollTop>0)||d.querySelector('dialog[open]'))return false;" +
                "var oh=function(e){var s=getComputedStyle(e);return s.overflowY==='hidden'||s.overflow==='hidden';};" +
                "if(oh(b)||oh(d.documentElement))return false;" +
                "for(var el=d.elementFromPoint(x,y);el&&el!==b&&el!==d.documentElement;el=el.parentElement){" +
                "var t=el.tagName;if(t==='CANVAS'||t==='INPUT'||t==='TEXTAREA'||t==='SELECT'||el.isContentEditable)return false;" +
                "var c=el.classList;if(c&&(c.contains('maplibregl-map')||c.contains('leaflet-container')))return false;" +
                "var s=getComputedStyle(el),ta=s.touchAction||'';" +
                "if(ta==='none'||(ta.indexOf('pan-x')>=0&&ta.indexOf('pan-y')<0))return false;" +
                "if((s.overflowX==='auto'||s.overflowX==='scroll')&&el.scrollWidth>el.clientWidth+1)return false;" +
                "if((s.overflowY==='auto'||s.overflowY==='scroll')&&el.scrollHeight>el.clientHeight+1)return false;}" +
                "return true;}catch(e){return false;}})"
    }
}
