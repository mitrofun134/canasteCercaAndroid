package ar.canastacerca

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runners.MethodSorters
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Тест оболочки на эмуляторе (T7): запуск и «Saltar», «назад» и окна сайта, наши ссылки canastacerca.com, экран
 * «нет сети» и «Reintentar», сервер, который не отвечает (30 с — экран ошибки), «потянуть — обновить», мост
 * CanastaApp.info() (с fontScale), крупный шрифт системы, ширина главной на 412 и 360 dp. На WebView ниже
 * BuildConfig.MIN_WEBVIEW (образ Android 10 — 74) сайта в приложении нет: проверяется экран «Actualizá … WebView» (t09),
 * остальное пропускается.
 *
 * Сайт — демо-сервер на компьютере: debug-сборка смотрит на http://10.0.2.2:3000/ (эмулятор так видит компьютер).
 * Снимки экрана — в /sdcard/Download/cc-ci: CI забирает их в артефакты прогона (tools/ci/emulator-run.sh).
 * Аргумент ccBreak=1 — намеренно красный прогон (приёмка T7).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ShellTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val device = UiDevice.getInstance(instr)

    /** Упавшая проверка — снимок экрана, каким его оставила проверка. */
    @get:Rule
    val failShot = object : TestWatcher() {
        override fun failed(e: Throwable, description: Description) {
            shot("FAIL-${description.methodName}")
        }
    }

    /** Медленный эмулятор показывает «System UI isn't responding»: такое окно забирает касания и «назад» — закрываем. */
    @Before
    fun closeSystemDialogs() {
        dismissSystemDialogs()
    }

    @After
    fun restore() {
        if (Build.VERSION.SDK_INT >= 30) device.executeShellCommand("cmd connectivity airplane-mode disable")
        device.executeShellCommand("svc wifi enable")
        device.executeShellCommand("svc data enable")
        device.executeShellCommand("settings put system font_scale 1.0")
        // Следующая проверка — с сетью: ждём, пока сервер сайта снова виден из приложения
        val until = SystemClock.elapsedRealtime() + WAIT
        while (!serverUp() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(500L)
    }

    private fun dismissSystemDialogs() {
        for (i in 0 until 3) {
            val wait = device.findObject(By.res("android", "aerr_wait")) ?: device.findObject(By.res("android", "aerr_close")) ?: return
            Log.w(TAG, "системное окно «не отвечает» — закрываю")
            wait.click()
            SystemClock.sleep(500L)
        }
    }

    /**
     * 0. Первый запуск без сети — экран «No pudimos abrir…» с «Reintentar»; сеть вернулась — «Reintentar» открывает сайт.
     * Идёт первым: после первой загрузки у сайта есть service worker, и без сети сайт отвечает сам (сохранённой
     * страницей или своим «Sin conexión») — экран оболочки тогда не нужен. В CI приложение каждый раз ставится заново.
     */
    @Test
    fun t00_offlineFirstStart() {
        assumeSite()
        network(false)
        val old = activity()
        ctx.startActivity(
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        waitActivity { it !== old }
        dismissSystemDialogs()
        val retry = device.wait(Until.findObject(By.text(ctx.getString(R.string.retry))), WAIT)
        assertNotNull("без сети нет экрана с «Reintentar» (${appState()})", retry)
        assertTrue("без сети нет заголовка «${ctx.getString(R.string.error_title)}»", device.hasObject(By.text(ctx.getString(R.string.error_title))))
        shot("00-offline")
        network(true)
        device.findObject(By.text(ctx.getString(R.string.retry)))?.click()
        waitJs(SITE_READY, "«Reintentar» не открыл сайт после возврата сети")
        assertTrue("экран «нет сети» не ушёл", device.wait(Until.gone(By.text(ctx.getString(R.string.retry))), 5_000L) == true)
        shot("00-online")
    }

    /** 1. Запуск — сайт. Первый раз «Ofertas» из меню — лист «¿Con qué pagás?», «Saltar» его закрывает. */
    @Test
    fun t01_launchAndSkip() {
        assumeSite()
        launch(fresh = true)
        shot("01-start")
        js("navigate('/recetas');setTimeout(function(){navigate('/')},400);true")
        waitJs(
            "!!document.querySelector('#dlg-pay-onb[open]')&&!!document.querySelector('#pay-onb-skip')&&" +
                "document.querySelector('#pay-onb-skip').getBoundingClientRect().height>0",
            "нет листа «¿Con qué pagás?» с кнопкой «Saltar» (${siteState()})",
        )
        SystemClock.sleep(600L) // лист выезжает
        shot("01-pay-sheet")
        assertEquals("на кнопке не «Saltar»", "\"Saltar\"", js("document.querySelector('#pay-onb-skip').textContent.trim()"))
        dismissSystemDialogs()
        tapSite("#pay-onb-skip")
        waitJs("!document.querySelector('dialog[open]')", "«Saltar» не закрыл лист")
        shot("01-after-skip")
    }

    /** 2. «Назад» закрывает открытое окно сайта (<dialog>), а приложение остаётся на экране. */
    @Test
    fun t02_backClosesSiteDialog() {
        assumeSite()
        launch()
        js("document.getElementById('dlg-loc').showModal();true")
        waitJs("!!document.querySelector('dialog[open]')", "окно адреса не открылось")
        shot("02-dialog")
        dismissSystemDialogs()
        device.pressBack()
        waitJs("!document.querySelector('dialog[open]')", "«назад» не закрыл окно сайта")
        SystemClock.sleep(500L)
        assertEquals("«назад» закрыл приложение", ctx.packageName, device.currentPackageName)
        assertNotNull("экран приложения закрылся", activity())
        shot("02-after-back")
    }

    /** 3. Ссылка https://canastacerca.com/… — в приложении, на сервере сборки: с нуля и в уже открытом приложении. */
    @Test
    fun t03_appLinks() {
        assumeSite()
        val old = activity()
        link("https://canastacerca.com/p/banana", cold = true)
        waitActivity { it !== old }
        waitJs("location.pathname==='/p/banana'&&document.readyState==='complete'", "ссылка /p/banana не открылась (с нуля)")
        assertEquals("ссылка открылась не на сервере сборки", JSONObject.quote(siteHost()), js("location.host"))
        shot("03-link-cold")
        link("https://canastacerca.com/recetas", cold = false)
        waitJs("location.pathname==='/recetas'&&document.readyState==='complete'", "ссылка /recetas не открылась в открытом приложении")
        shot("03-link-warm")
    }

    /**
     * 4. Сервер принимает соединение и молчит (сеть «висит», W329 п. 5): через 30 с без первого кадра — экран
     * «No pudimos abrir…» с «Reintentar», а не Firu до бесконечности. «Висящий» сервер — в этом же процессе, адрес —
     * ссылкой отладочной сборки (как вставленная на экране ошибки), потом убирается.
     */
    @Test
    fun t04_stalledServer() {
        assumeSite()
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val held = Collections.synchronizedList(mutableListOf<Socket>())
        Thread {
            runCatching { while (true) held += server.accept() }
        }.apply { isDaemon = true }.start()
        val prefs = ctx.getSharedPreferences("canasta", Context.MODE_PRIVATE)
        try {
            prefs.edit()
                .putString("server_link", "http://127.0.0.1:${server.localPort}/")
                .putString("server_link_build", BuildConfig.CANASTA_BASE_URL + "|" + BuildConfig.CANASTA_ACCESS_KEY)
                .commit()
            val old = activity()
            ctx.startActivity(
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            waitActivity { it !== old }
            val started = SystemClock.elapsedRealtime()
            val retry = device.wait(Until.findObject(By.text(ctx.getString(R.string.retry))), 50_000L)
            assertNotNull("сервер молчит 50 с, а экрана «Reintentar» нет (${appState()})", retry)
            val took = (SystemClock.elapsedRealtime() - started) / 1000
            assertTrue("сервер не получил ни одного соединения — проверка не про «висящую» сеть", held.isNotEmpty())
            assertTrue("экран ошибки через $took с — раньше 30 с, хотя сервер принял соединение", took >= 25)
            assertTrue("нет заголовка «${ctx.getString(R.string.error_title)}»", device.hasObject(By.text(ctx.getString(R.string.error_title))))
            shot("04-stalled-${took}s")
        } finally {
            prefs.edit().remove("server_link").remove("server_link_build").commit()
            runCatching { server.close() }
            held.forEach { runCatching { it.close() } }
        }
    }

    /** 5. «Потянуть вниз» сверху страницы — страница загружается заново (метка в window пропадает). */
    @Test
    fun t05_pullToRefresh() {
        assumeSite()
        launch()
        js("location.href='/privacidad';true")
        waitJs("location.pathname==='/privacidad'&&document.readyState==='complete'", "/privacidad не открылась")
        js("window.__ccPull=1;scrollTo(0,0);true")
        SystemClock.sleep(300L)
        val r = Rect()
        val a = activity()
        instr.runOnMainSync { a?.findViewById<WebView>(R.id.web)?.getGlobalVisibleRect(r) }
        assertTrue("WebView не на экране", r.height() > 0)
        dismissSystemDialogs()
        device.swipe(r.centerX(), r.top + r.height() / 5, r.centerX(), r.top + r.height() * 3 / 4, 40)
        shot("05-pull")
        waitJs("document.readyState==='complete'&&typeof window.__ccPull==='undefined'", "«потянуть — обновить» не обновил страницу")
        shot("05-after-pull")
    }

    /** 6. Мост: CanastaApp.info() — платформа android и версия этой сборки. */
    @Test
    fun t06_bridgeInfo() {
        assumeSite()
        // Старый WebView (без DOCUMENT_START_SCRIPT и WEB_MESSAGE_LISTENER) моста не получает: сайт — как в браузере
        assumeTrue("WebView без моста: ${WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName}", bridgeSupported())
        launch()
        val raw = js("window.CanastaApp?CanastaApp.info():null")
        assertTrue("нет window.CanastaApp", raw != null && raw != "null")
        val info = JSONObject(JSONArray("[$raw]").getString(0))
        assertEquals("android", info.optString("platform"))
        assertEquals("${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}", info.optString("version"))
        assertEquals("true", js("typeof CanastaApp.haptic==='function'"))
        // T5: масштаб шрифта телефона — для подсказки «Letra grande» (после каждой проверки шрифт — 1,0)
        assertEquals("fontScale в info()", 1.0, info.optDouble("fontScale", -1.0), 0.001)
    }

    /** 7. Шрифт системы 1,3: экран пересоздаётся, сайт — без прокрутки вбок. */
    @Test
    fun t07_fontScale() {
        assumeSite()
        val old = launch()
        device.executeShellCommand("settings put system font_scale 1.3")
        waitActivity { it !== old }
        waitJs(SITE_READY, "после смены шрифта сайт не открылся")
        assertEquals("при шрифте 1,3 страница шире экрана", "true", js(NO_SIDE_SCROLL))
        // T5: сайт узнаёт крупный шрифт телефона из CanastaApp.info() — подсказка «Letra grande»
        if (bridgeSupported()) {
            val raw = js("window.CanastaApp?CanastaApp.info():null")
            val info = JSONObject(JSONArray("[$raw]").getString(0))
            assertEquals("fontScale в info() при шрифте 1,3", 1.3, info.optDouble("fontScale", -1.0), 0.001)
        }
        shot("07-font-1.3")
    }

    /**
     * 8. Главная без прокрутки вбок: на этом экране и на узком 360 dp (плотность под ширину экрана в px). С 0.3.5 сайт
     * идёт только на WebView 105+, а он в CI только у Android 14 (412 dp): 360 dp проверяем там же.
     */
    @Test
    fun t08_homeWidth() {
        assumeSite()
        val a = launch()
        homeFits()
        val res = a.resources
        if (res.configuration.screenWidthDp <= 360) return
        val orig = res.displayMetrics.densityDpi
        val narrow = minOf(res.displayMetrics.widthPixels, res.displayMetrics.heightPixels) * 160 / 360
        try {
            device.executeShellCommand("wm density $narrow")
            waitActivity { it !== a && it.resources.configuration.screenWidthDp <= 361 }
            waitJs(SITE_READY, "на 360 dp сайт не открылся")
            waitNoOverlay()
            homeFits()
        } finally {
            device.executeShellCommand("wm density $orig")
            SystemClock.sleep(1_000L)
        }
    }

    private fun homeFits() {
        val w = js("innerWidth")
        assertEquals("главная шире экрана ($w px)", "true", js(NO_SIDE_SCROLL))
        shot("08-home-${w}px")
    }

    /**
     * 9. WebView ниже BuildConfig.MIN_WEBVIEW (ГД 25.11; образ Android 10 — 74): вместо сайта экран «Actualizá … WebView»
     * с кнопкой в Google Play, сайт под ним не грузится. На свежем WebView — пропуск.
     */
    @Test
    fun t09_oldWebView() {
        val major = webViewMajor()
        assumeTrue("WebView $major — не ниже ${BuildConfig.MIN_WEBVIEW}", major in 1 until BuildConfig.MIN_WEBVIEW)
        val old = activity()
        ctx.startActivity(
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val a = waitActivity { it !== old }
        dismissSystemDialogs()
        val button = device.wait(Until.findObject(By.res(ctx.packageName, "update_button")), WAIT)
        assertNotNull("WebView $major: нет экрана с «${ctx.getString(R.string.update_button)}» (${appState()})", button)
        val title = device.findObject(By.res(ctx.packageName, "update_title"))?.text.orEmpty()
        assertTrue("заголовок не про WebView: «$title»", title.contains("WebView") || title.contains("Chrome"))
        var url: String? = "?"
        instr.runOnMainSync { url = a.findViewById<WebView>(R.id.web).url }
        assertTrue("под экраном обновления грузился сайт: $url", url.isNullOrEmpty() || url == "about:blank")
        shot("09-old-webview-$major")
    }

    /** Приёмка T7: с аргументом ccBreak=1 эта проверка падает, и прогон должен стать красным. */
    @Test
    fun t99_breakOnPurpose() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("ccBreak") == "1")
        fail("Намеренно сломанная проверка (приёмка T7: workflow_dispatch → break_check)")
    }

    /* ---------- Помощники ---------- */

    /** Приложение с нуля (новый экран), сайт загружен. fresh — ещё и пустой localStorage сайта, как при первом запуске. */
    private fun launch(fresh: Boolean = false): MainActivity {
        val old = activity()
        val start = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(start)
        val a = waitActivity { it !== old }
        waitJs(SITE_READY, "сайт не открылся")
        waitNoOverlay()
        if (fresh) {
            js("window.__ccOld=1;localStorage.clear();sessionStorage.clear();location.reload();true")
            waitJs("typeof window.__ccOld==='undefined'&&$SITE_READY", "сайт не открылся заново с пустым localStorage")
        }
        return a
    }

    /** Наша ссылка, как из WhatsApp: cold — приложение с нуля, иначе — в уже открытом (onNewIntent). */
    private fun link(url: String, cold: Boolean) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or (if (cold) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(ctx.packageName).addFlags(flags),
        )
    }

    /** Для сообщения об ошибке: какой WebView и дошли ли скрипты сайта до конца. */
    private fun siteState(): String =
        "WebView ${WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName}, " +
            "navigate: ${js("typeof navigate")}, payOnboardMaybe: ${js("typeof payOnboardMaybe")}, " +
            "адрес: ${js("location.pathname")}, окно: ${js("(document.querySelector('dialog[open]')||{}).id||''")}"

    private fun siteHost(): String = Uri.parse(BuildConfig.CANASTA_BASE_URL).encodedAuthority.orEmpty()

    private fun activity(): MainActivity? {
        var a: MainActivity? = null
        instr.runOnMainSync {
            a = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return a
    }

    private fun waitActivity(ok: (MainActivity) -> Boolean): MainActivity {
        val until = SystemClock.elapsedRealtime() + WAIT
        while (SystemClock.elapsedRealtime() < until) {
            activity()?.let { if (ok(it)) return it }
            SystemClock.sleep(200L)
        }
        fail("экран приложения не открылся за ${WAIT / 1000} с")
        throw AssertionError()
    }

    /** JS на странице текущего экрана; ответ — как у evaluateJavascript (JSON), null — нет экрана или ответа. */
    private fun js(script: String, timeoutMs: Long = 10_000L): String? {
        val a = activity() ?: return null
        val done = CountDownLatch(1)
        var out: String? = null
        instr.runOnMainSync {
            runCatching {
                a.findViewById<WebView>(R.id.web).evaluateJavascript(script) {
                    out = it
                    done.countDown()
                }
            }.onFailure { done.countDown() }
        }
        return if (done.await(timeoutMs, TimeUnit.MILLISECONDS)) out else null
    }

    private fun waitJs(script: String, what: String, timeoutMs: Long = WAIT) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (js(script, 5_000L) == "true") return
            SystemClock.sleep(250L)
        }
        fail("$what (ждали ${timeoutMs / 1000} с; ${appState()})")
    }

    /** Что на экране приложения поверх сайта — для сообщения об ошибке. */
    private fun appState(): String {
        val a = activity() ?: return "экрана приложения нет, на экране ${device.currentPackageName}"
        val on = mutableListOf<String>()
        instr.runOnMainSync {
            for ((id, name) in listOf(R.id.loading to "Firu-заставка", R.id.freeze to "снимок страницы", R.id.error to "экран ошибки", R.id.update to "«Actualizá la app»")) {
                if (a.findViewById<View>(id)?.visibility == View.VISIBLE) on += name
            }
        }
        return "поверх сайта: ${on.joinToString().ifEmpty { "ничего" }}, адрес ${js("location.pathname", 3_000L)}"
    }

    /** Пока поверх сайта заставка или снимок — касания до сайта не доходят: ждём. */
    private fun waitNoOverlay() {
        val until = SystemClock.elapsedRealtime() + WAIT
        while (SystemClock.elapsedRealtime() < until) {
            val a = activity()
            var busy = true
            instr.runOnMainSync {
                busy = a == null || listOf(R.id.loading, R.id.freeze).any { a.findViewById<View>(it)?.visibility == View.VISIBLE }
            }
            if (!busy) return
            SystemClock.sleep(200L)
        }
        fail("заставка или снимок не ушли (${appState()})")
    }

    /** Настоящее касание по элементу сайта: середина его прямоугольника в координатах экрана. */
    private fun tapSite(selector: String) {
        val raw = js("(function(){var r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2]})()")
        val xy = JSONArray(raw ?: "[]")
        assertTrue("нет элемента $selector", xy.length() == 2)
        val a = activity()!!
        val at = IntArray(2)
        var density = 1f
        instr.runOnMainSync {
            a.findViewById<WebView>(R.id.web).getLocationOnScreen(at)
            density = a.resources.displayMetrics.density
        }
        device.click((at[0] + xy.getDouble(0) * density).toInt(), (at[1] + xy.getDouble(1) * density).toInt())
    }

    /** Сайт в приложении есть только на WebView от BuildConfig.MIN_WEBVIEW (ниже — экран обновления, t09). */
    private fun assumeSite() {
        val wv = WebViewCompat.getCurrentWebViewPackage(ctx)
        Log.i(TAG, "API ${Build.VERSION.SDK_INT}, WebView ${wv?.packageName} ${wv?.versionName}")
        assumeTrue("WebView ${wv?.versionName}: сайту нужен ${BuildConfig.MIN_WEBVIEW}+", webViewMajor() >= BuildConfig.MIN_WEBVIEW)
    }

    private fun bridgeSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    private fun webViewMajor(): Int =
        WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0

    /**
     * Сеть эмулятора. Android 11+ — режим полёта: так система говорит «сети нет», и WebView сразу отдаёт ошибку
     * (на Android 14 после svc wifi/data соединения к 10.0.2.2 висят, а не падают). Android 10 — svc wifi и data.
     * Ждём, пока сервер сайта станет (не)доступен из приложения.
     */
    private fun network(on: Boolean) {
        val v = if (on) "enable" else "disable"
        if (Build.VERSION.SDK_INT >= 30) {
            device.executeShellCommand("cmd connectivity airplane-mode ${if (on) "disable" else "enable"}")
        } else {
            device.executeShellCommand("svc wifi $v")
            device.executeShellCommand("svc data $v")
        }
        val until = SystemClock.elapsedRealtime() + WAIT
        while (SystemClock.elapsedRealtime() < until) {
            if (serverUp() == on) return
            SystemClock.sleep(500L)
        }
        fail(if (on) "сеть не вернулась" else "сеть не отключилась (svc wifi/data)")
    }

    private fun serverUp(): Boolean = runCatching {
        val http = URL(BuildConfig.CANASTA_BASE_URL.trimEnd('/') + "/healthz").openConnection() as HttpURLConnection
        try {
            http.connectTimeout = 2_000
            http.readTimeout = 2_000
            http.responseCode == 200
        } finally {
            http.disconnect()
        }
    }.getOrDefault(false)

    /** Снимок экрана в /sdcard/Download/cc-ci (screencap от имени shell: так пишется на любой версии Android). */
    private fun shot(name: String) {
        runCatching {
            device.executeShellCommand("mkdir -p $SHOTS")
            device.executeShellCommand("screencap -p $SHOTS/$name.png")
        }
    }

    private companion object {
        const val TAG = "CanastaCI"
        const val WAIT = 30_000L
        const val SHOTS = "/sdcard/Download/cc-ci"
        const val SITE_READY = "document.readyState==='complete'&&!!document.querySelector('.appbar')"
        const val NO_SIDE_SCROLL =
            "document.documentElement.scrollWidth<=innerWidth+1&&(!document.body||document.body.scrollWidth<=innerWidth+1)"
    }
}
