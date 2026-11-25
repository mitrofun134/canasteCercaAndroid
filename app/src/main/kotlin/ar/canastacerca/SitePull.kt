package ar.canastacerca

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * «Потянуть вниз — обновить» над WebView (T3). SwipeRefreshLayout сам знает только одно: страница не в самом верху
 * (canScrollVertically). Можно ли тянуть из точки касания — карусель, карта, открытая панель — знает только сайт
 * (MainActivity.PULL_OK), и его ответ приходит через несколько миллисекунд после касания. До ответа тянуть нельзя:
 * касание проходит в сайт как обычно, а круг появляется, как только сайт ответил «можно» и палец ушёл вниз.
 */
class SitePull @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SwipeRefreshLayout(context, attrs) {

    /** Начало касания: x, y — в CSS px страницы; gesture — номер касания, его же вернуть в answer(). */
    var onTouchDown: ((x: Float, y: Float, gesture: Int) -> Unit)? = null

    /** Что листается (WebView): прямой ребёнок — контейнер со снимком страницы, сам он не листается. */
    var scrollTarget: View? = null

    private var gesture = 0
    private var allowed = false

    /** Пока SwipeRefreshLayout разбирает само касание (ACTION_DOWN), ответа сайта ещё нет — не мешаем начать. */
    private var inDown = false

    init {
        setOnChildScrollUpCallback { _, child ->
            (scrollTarget ?: child)?.canScrollVertically(-1) == true || (!inDown && !allowed)
        }
    }

    /** Ответ сайта для касания gesture; ответ на прошлое касание не считается. */
    fun answer(gesture: Int, ok: Boolean) {
        if (gesture == this.gesture) allowed = ok
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gesture++
            allowed = false
            val density = resources.displayMetrics.density
            onTouchDown?.invoke(ev.x / density, ev.y / density, gesture)
            inDown = true
            try {
                return super.onInterceptTouchEvent(ev)
            } finally {
                inDown = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
