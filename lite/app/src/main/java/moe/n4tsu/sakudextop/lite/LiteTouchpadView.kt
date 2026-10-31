package moe.n4tsu.sakudextop.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** A phone-side laptop-style trackpad: move, tap, long-press-drag and two-finger scroll. */
internal class LiteTouchpadView(context: Context) : View(context) {
    interface Listener {
        fun move(dx: Float, dy: Float)
        fun tap()
        fun dragStart()
        fun dragMove(dx: Float, dy: Float)
        fun dragEnd()
        fun scroll(amount: Float)
    }

    var listener: Listener? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF171A20.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF39414D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8AB4F8.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8C0CC.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
    }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF788392.toInt()
        textAlign = Paint.Align.CENTER
        textSize = dp(11f)
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var moved = false
    private var dragging = false
    private var scrolling = false
    private var lastScrollY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bounds, dp(22f), dp(22f), fill)
        canvas.drawRoundRect(bounds, dp(22f), dp(22f), edge)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, dp(22f), edge)
        canvas.drawCircle(cx, cy, dp(4f), accent)
        canvas.drawText("SENTUH DAN GESER", cx, cy + dp(58f), text)
        canvas.drawText("Ketuk = klik  •  tahan lalu geser = drag", cx, cy + dp(82f), subText)
        canvas.drawText("Dua jari = scroll", cx, cy + dp(103f), subText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                downAt = event.eventTime
                moved = false
                dragging = false
                scrolling = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    scrolling = true
                    moved = true
                    lastScrollY = averageY(event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scrolling && event.pointerCount >= 2) {
                    val current = averageY(event)
                    val delta = current - lastScrollY
                    lastScrollY = current
                    if (abs(delta) >= 1f) listener?.scroll(-delta / dp(42f))
                    return true
                }
                if (event.pointerCount != 1) return true

                val x = event.x
                val y = event.y
                val dx = x - lastX
                val dy = y - lastY
                lastX = x
                lastY = y
                if (!moved && (abs(x - downX) > dp(4f) || abs(y - downY) > dp(4f))) {
                    moved = true
                }
                if (moved) {
                    if (!dragging && event.eventTime - downAt >= DRAG_HOLD_MS) {
                        dragging = true
                        listener?.dragStart()
                    }
                    if (dragging) listener?.dragMove(dx, dy) else listener?.move(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (scrolling) scrolling = false
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    listener?.dragEnd()
                } else if (!scrolling && !moved) {
                    listener?.tap()
                }
                resetGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) listener?.dragEnd()
                resetGesture()
                return true
            }
        }
        return true
    }

    private fun averageY(event: MotionEvent): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) total += event.getY(index)
        return total / event.pointerCount
    }

    private fun resetGesture() {
        parent?.requestDisallowInterceptTouchEvent(false)
        moved = false
        dragging = false
        scrolling = false
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val DRAG_HOLD_MS = 320L
    }
}
