package ar.canastacerca

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.webkit.WebViewCompat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
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
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Тест оболочки на эмуляторе (T7): запуск и «Saltar», «назад» и окна сайта, наши ссылки canastacerca.com, экран
 * «нет сети» и «Reintentar», «потянуть — обновить», мост CanastaApp.info(), крупный шрифт системы, ширина главной.
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

    @After
    fun restore() {
        device.executeShellCommand("svc wifi enable")
        device.executeShellCommand("svc data enable")
        device.executeShellCommand("settings put system font_scale 1.0")
    }

    /** 1. Запуск — сайт. Первый раз «Ofertas» из меню — лист «¿Con qué pagás?», «Saltar» его закрывает. */
    @Test
    fun t01_launchAndSkip() {
        launch(fresh = true)
        val wv = WebViewCompat.getCurrentWebViewPackage(ctx)
        Log.i(TAG, "API ${Build.VERSION.SDK_INT}, WebView ${wv?.packageName} ${wv?.versionName}, страница ${js("innerWidth")} px")
        shot("01-start")
        js("navigate('/recetas');setTimeout(function(){navigate('/')},400);true")
        val skip = device.wait(Until.findObject(By.text("Saltar")), WAIT)
        assertNotNull("нет листа «¿Con qué pagás?» с кнопкой «Saltar»", skip)
        shot("01-pay-sheet")
        skip!!.click()
        waitJs("!document.querySelector('dialog[open]')", "«Saltar» не закрыл лист")
        shot("01-after-skip")
    }

    /** 2. «Назад» закрывает открытое окно сайта (<dialog>), а приложение остаётся на экране. */
    @Test
    fun t02_backClosesSiteDialog() {
        launch()
        js("document.getElementById('dlg-loc').showModal();true")
        waitJs("!!document.querySelector('dialog[open]')", "окно адреса не открылось")
        shot("02-dialog")
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

    /** 4. Без сети — экран «No pudimos abrir…» с «Reintentar»; сеть вернулась — «Reintentar» открывает сайт. */
    @Test
    fun t04_offlineAndRetry() {
        launch()
        // Без service worker сайта: иначе он сам покажет сохранённую страницу, и до экрана оболочки дело не дойдёт
        js(
            "(function(){if(navigator.serviceWorker)navigator.serviceWorker.getRegistrations().then(function(r){" +
                "r.forEach(function(x){x.unregister()})});if(window.caches)caches.keys().then(function(k){" +
                "k.forEach(function(n){caches.delete(n)})});return true})()",
        )
        SystemClock.sleep(1500L)
        network(false)
        instr.runOnMainSync { activity()?.findViewById<WebView>(R.id.web)?.reload() }
        val retry = device.wait(Until.findObject(By.text(ctx.getString(R.string.retry))), WAIT)
        assertNotNull("без сети нет экрана с «Reintentar»", retry)
        assertTrue("без сети нет заголовка «${ctx.getString(R.string.error_title)}»", device.hasObject(By.text(ctx.getString(R.string.error_title))))
        shot("04-offline")
        network(true)
        device.findObject(By.text(ctx.getString(R.string.retry)))?.click()
        waitJs(SITE_READY, "«Reintentar» не открыл сайт после возврата сети")
        assertTrue("экран «нет сети» не ушёл", device.wait(Until.gone(By.text(ctx.getString(R.string.retry))), 5_000L) == true)
        shot("04-online")
    }

    /** 5. «Потянуть вниз» сверху страницы — страница загружается заново (метка в window пропадает). */
    @Test
    fun t05_pullToRefresh() {
        launch()
        js("location.href='/privacidad';true")
        waitJs("location.pathname==='/privacidad'&&document.readyState==='complete'", "/privacidad не открылась")
        js("window.__ccPull=1;scrollTo(0,0);true")
        SystemClock.sleep(300L)
        val r = Rect()
        instr.runOnMainSync { activity()?.findViewById<WebView>(R.id.web)?.getGlobalVisibleRect(r) }
        assertTrue("WebView не на экране", r.height() > 0)
        device.swipe(r.centerX(), r.top + r.height() / 5, r.centerX(), r.top + r.height() * 3 / 4, 40)
        shot("05-pull")
        waitJs("document.readyState==='complete'&&typeof window.__ccPull==='undefined'", "«потянуть — обновить» не обновил страницу")
        shot("05-after-pull")
    }

    /** 6. Мост: CanastaApp.info() — платформа android и версия этой сборки. */
    @Test
    fun t06_bridgeInfo() {
        launch()
        val raw = js("window.CanastaApp?CanastaApp.info():null")
        assertTrue("нет window.CanastaApp", raw != null && raw != "null")
        val info = JSONObject(JSONArray("[$raw]").getString(0))
        assertEquals("android", info.optString("platform"))
        assertEquals("${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}", info.optString("version"))
        assertEquals("true", js("typeof CanastaApp.haptic==='function'"))
    }

    /** 7. Шрифт системы 1,3: экран пересоздаётся, сайт — без прокрутки вбок. */
    @Test
    fun t07_fontScale() {
        val old = launch()
        device.executeShellCommand("settings put system font_scale 1.3")
        waitActivity { it !== old }
        waitJs(SITE_READY, "после смены шрифта сайт не открылся")
        assertEquals("при шрифте 1,3 страница шире экрана", "true", js(NO_SIDE_SCROLL))
        shot("07-font-1.3")
    }

    /** 8. Главная на этом экране (360 или 412 dp) — без прокрутки вбок. */
    @Test
    fun t08_homeWidth() {
        launch()
        val w = js("innerWidth")
        assertEquals("главная шире экрана ($w px)", "true", js(NO_SIDE_SCROLL))
        shot("08-home-${w}px")
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
        fail("$what (ждали ${timeoutMs / 1000} с)")
    }

    /** Сеть эмулятора: wifi и мобильные данные. Ждём, пока сервер сайта станет (не)доступен из приложения. */
    private fun network(on: Boolean) {
        val v = if (on) "enable" else "disable"
        device.executeShellCommand("svc wifi $v")
        device.executeShellCommand("svc data $v")
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
