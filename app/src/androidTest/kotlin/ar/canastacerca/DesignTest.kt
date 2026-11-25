package ar.canastacerca

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.webkit.WebViewCompat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Снимки и замер экранов для дизайнера (T7, D96) — тем же путём, что и тест оболочки: сайт в WebView приложения,
 * снимок — screencap от имени shell (как видит человек: со строкой состояния и меню телефона), замер — по правилам
 * design/specs (assets/design-measure.js) через evaluateJavascript. Это не проверка: ничего не роняет.
 * Каждый экран — строкой «INSTRUMENTATION_STATUS: design=<JSON>»; tools/ci/design/report.js собирает из них index.html,
 * отметку и сводку прогона. Снимки — /sdcard/Download/cc-design. Запуск — tools/ci/emulator-run.sh:
 *   am instrument -w -r -e class ar.canastacerca.DesignTest -e ccDesign 412 ar.canastacerca.test/androidx.test.runner.AndroidJUnitRunner
 */
class DesignTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val device = UiDevice.getInstance(instr)

    private class Screen(val id: String, val path: String, val what: String, val calc: Boolean = false)

    @Test
    fun screens() {
        val major = WebViewCompat.getCurrentWebViewPackage(ctx)?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
        assumeTrue("WebView $major: сайту нужен ${BuildConfig.MIN_WEBVIEW}+", major >= BuildConfig.MIN_WEBVIEW)
        val tag = InstrumentationRegistry.getArguments().getString("ccDesign") ?: ctx.resources.configuration.screenWidthDp.toString()
        val measure = instr.context.assets.open("design-measure.js").bufferedReader().use { it.readText() }
        val base = BuildConfig.CANASTA_BASE_URL.trimEnd('/')
        device.executeShellCommand("mkdir -p $SHOTS")

        // Приложение с нуля; состояние сайта — адрес приёмки QA, испанский, без листа оплаты, корзина из демо
        val old = activity()
        ctx.startActivity(
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        waitFor(30_000L) { activity()?.let { it !== old } == true }
        waitFor(30_000L) { js("document.readyState==='complete'") == "true" }
        val basket = JSONArray()
        for (cat in listOf("yerba", "leche")) {
            val items = runCatching { JSONObject(get("$base/api/catalog?cat=$cat")).optJSONArray("items") }.getOrNull() ?: continue
            for (i in 0 until minOf(3, items.length())) {
                val x = items.getJSONObject(i)
                basket.put(
                    JSONObject().put("id", x.opt("id")).put("name", x.opt("name")).put("brand", x.opt("brand"))
                        .put("presentation", x.opt("presentation")).put("qty", 1).put("scope", "super").put("prices", JSONObject.NULL)
                        .put("offers", JSONObject()).put("pays", JSONObject()),
                )
            }
        }
        val state = JSONObject().put("loc", LOC).put("lang", "es").put("radiusKm", 2).put("onboardPay", "done").put("basket", basket)
        js("localStorage.setItem('canasta-cerca-v1',${JSONObject.quote(state.toString())});true")

        for (s in SCREENS) {
            val out = JSONObject().put("id", s.id).put("path", s.path).put("what", s.what).put("dp", tag)
            try {
                js("window.__ccNav=1;true")
                val a = activity() ?: error("экрана приложения нет")
                instr.runOnMainSync { a.findViewById<WebView>(R.id.web).loadUrl(base + s.path) }
                waitFor(40_000L) { js("typeof window.__ccNav==='undefined'&&document.readyState==='complete'") == "true" }
                if (s.calc) {
                    js("(function(){var b=document.querySelector('#calc-btn');if(b&&!b.disabled)b.click();return true})()")
                    waitFor(25_000L) { js("!!(document.querySelector('#results')||{}).innerText") == "true" }
                }
                // Дорисовался: нет видимых заглушек и aria-busy, поверх сайта нет заставки и снимка — но не дольше 8 с
                runCatching { waitFor(8_000L) { js(SETTLED) == "true" && !overlay() } }
                SystemClock.sleep(700L)
                val first = "$tag-${s.id}-1.png"
                device.executeShellCommand("screencap -p $SHOTS/$first")
                val m = JSONObject(js(measure, 20_000L) ?: error("замер не ответил"))
                js("window.scrollTo(0,innerHeight*0.9);true")
                SystemClock.sleep(800L)
                val second = "$tag-${s.id}-2.png"
                device.executeShellCommand("screencap -p $SHOTS/$second")
                m.put("fixedAfter", js(FIXED_AFTER)?.toIntOrNull())
                out.put("m", m).put("shots", JSONArray().put(first).put(second))
            } catch (e: Throwable) {
                out.put("error", (e.message ?: e.toString()).lineSequence().first().take(200))
            }
            instr.sendStatus(2, Bundle().apply { putString("design", out.toString()) })
        }
    }

    /* ---------- Помощники (как в ShellTest) ---------- */

    private fun activity(): MainActivity? {
        var a: MainActivity? = null
        instr.runOnMainSync {
            a = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return a
    }

    private fun overlay(): Boolean {
        val a = activity() ?: return true
        var on = true
        instr.runOnMainSync { on = listOf(R.id.loading, R.id.freeze).any { a.findViewById<View>(it)?.visibility == View.VISIBLE } }
        return on
    }

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

    private fun waitFor(timeoutMs: Long, ok: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < until) {
            if (ok()) return
            SystemClock.sleep(250L)
        }
        error("не дождались за ${timeoutMs / 1000} с")
    }

    private fun get(url: String): String {
        val http = URL(url).openConnection() as HttpURLConnection
        try {
            http.connectTimeout = 5_000
            http.readTimeout = 10_000
            return http.inputStream.bufferedReader().use { it.readText() }
        } finally {
            http.disconnect()
        }
    }

    private companion object {
        const val SHOTS = "/sdcard/Download/cc-design"

        // Адрес приёмки QA (qa/helpers/browser.js → CABA_LOC): для него у демо есть магазины рядом
        val LOC: JSONObject = JSONObject().put("lat", -34.553492488).put("lng", -58.453999699)
            .put("label", "Franklin D. Roosevelt 1700, Belgrano, Ciudad Autónoma de Buenos Aires").put("kind", "address")

        val SCREENS = listOf(
            Screen("inicio", "/", "Главная"),
            Screen("ofertas-coto", "/ofertas/coto", "Скидки сети"),
            Screen("catalogo", "/catalogo", "Каталог"),
            Screen("catalogo-yerba", "/catalogo/yerba", "Раздел каталога"),
            Screen("buscar-leche", "/buscar?q=leche", "Поиск «leche»"),
            Screen("producto-banana", "/p/banana", "Товар"),
            Screen("recetas", "/recetas", "Рецепты"),
            Screen("receta", "/recetas/milanesas-con-pure", "Рецепт"),
            Screen("descuentos", "/descuentos", "Скидки дня"),
            Screen("ahorro", "/ahorro", "«Mi ahorro»"),
            Screen("pagos", "/cuenta/pagos", "Способы оплаты"),
            // Последней: на эмуляторе на ней обрывался весь прогон (25.11) — остальные экраны успевают сняться
            Screen("canasta", "/canasta", "Корзина и ответ", calc = true),
        )

        const val SETTLED =
            "(function(){var n=document.querySelectorAll('[class*=\"skel\"],[aria-busy=\"true\"]');" +
                "for(var i=0;i<n.length;i++){var r=n[i].getBoundingClientRect();" +
                "if(r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight)return false;}return true;})()"

        /** Сколько экрана после прокрутки занимают fixed и sticky шириной больше половины экрана, %. */
        const val FIXED_AFTER =
            "(function(){var h=0,a=document.querySelectorAll('body *');for(var i=0;i<a.length;i++){var cs=getComputedStyle(a[i]);" +
                "if(cs.position!=='fixed'&&cs.position!=='sticky')continue;var r=a[i].getBoundingClientRect();" +
                "if(r.width<innerWidth/2||cs.visibility==='hidden'||cs.display==='none')continue;" +
                "var t=Math.max(0,r.top),b=Math.min(innerHeight,r.bottom);if(b>t)h+=b-t;}return Math.round(100*h/innerHeight)})()"
    }
}
