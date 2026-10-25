package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight MLBB-style touch mapper. Controls are movable in edit mode and
 * their normalized positions are stored per display resolution.
 */
internal class MlbbGamepadOverlayView(
    context: Context,
    private val dispatch: InputDispatcher,
    private val displayId: () -> Int,
    private val targetSize: () -> Pair<Int, Int>,
    private val onClose: () -> Unit
) : FrameLayout(context) {
    private data class Control(val label: String, var x: Float, var y: Float)
    private val prefs = context.getSharedPreferences("mlbb_gamepad_overlay", Context.MODE_PRIVATE)
    private val controls = mutableListOf(
        Control("MOVE", .16f, .72f), Control("ATTACK", .86f, .72f),
        Control("SKILL 1", .78f, .48f), Control("SKILL 2", .88f, .39f),
        Control("SKILL 3", .70f, .38f), Control("ULTI", .91f, .27f),
        Control("SPELL", .63f, .62f)
    )
    private val activePointers = linkedMapOf<Int, Pair<Float, Float>>()
    private val buttonPointers = linkedMapOf<Int, Control>()
    private var editing = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius get() = 29f * resources.displayMetrics.density

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isMotionEventSplittingEnabled = false
        val saved = prefs.getString("positions", null)
        if (saved != null) runCatching {
            val json = JSONObject(saved)
            controls.forEach { c ->
                if (json.has(c.label)) {
                    val p = json.getJSONObject(c.label)
                    c.x = p.optDouble("x", c.x.toDouble()).toFloat().coerceIn(.04f, .96f)
                    c.y = p.optDouble("y", c.y.toDouble()).toFloat().coerceIn(.08f, .92f)
                }
            }
        }
        val header = TextView(context).apply {
            text = "SAKU GAMEPAD   •   tahan tombol untuk geser   •   EDIT"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            setBackgroundColor(0xCC15191F.toInt()); setPadding(12, 8, 12, 8)
            setOnClickListener { editing = !editing; text = if (editing) "MODE EDIT AKTIF • sentuh untuk simpan posisi" else "SAKU GAMEPAD • tahan tombol untuk geser • EDIT"; invalidate() }
        }
        addView(header, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        val close = TextView(context).apply {
            text = "✕"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setBackgroundColor(0xCCB32626.toInt()); setPadding(18, 8, 18, 8)
            setOnClickListener { releaseAll(); onClose() }
        }
        addView(close, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        controls.forEach { c ->
            val cx = c.x * width; val cy = c.y * height
            paint.color = if (editing) 0xAAE6A23C.toInt() else 0x9951A9F5.toInt()
            canvas.drawCircle(cx, cy, radius, paint)
            paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER
            paint.textSize = if (c.label.length > 6) 10f * resources.displayMetrics.density else 12f * resources.displayMetrics.density
            canvas.drawText(c.label, cx, cy + paint.textSize * .35f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(index) / max(1, width)
                val y = event.getY(index) / max(1, height)
                val control = controls.minByOrNull { c -> (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y) }
                if (control != null && ((control.x - x) * width).let { it * it } + ((control.y - y) * height).let { it * it } < radius * radius * 2) {
                    buttonPointers[id] = control
                    activePointers[id] = control.x to control.y
                    if (editing) return true
                    send(event.eventTime, MotionEvent.ACTION_DOWN, listOf(control.x to control.y))
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                buttonPointers.keys.toList().forEach { pointerId ->
                    val pi = (0 until event.pointerCount).firstOrNull { event.getPointerId(it) == pointerId } ?: return@forEach
                    val c = buttonPointers[pointerId] ?: return@forEach
                    val x = event.getX(pi) / max(1, width)
                    val y = event.getY(pi) / max(1, height)
                    if (editing) {
                        c.x = x.coerceIn(.04f, .96f); c.y = y.coerceIn(.08f, .92f)
                        activePointers[pointerId] = c.x to c.y
                        invalidate()
                    } else {
                        activePointers[pointerId] = x to y
                        send(event.eventTime, MotionEvent.ACTION_MOVE, activePointers.values.toList())
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val c = buttonPointers.remove(id)
                if (c != null && !editing) send(event.eventTime, MotionEvent.ACTION_UP, activePointers.filterKeys { it != id }.values.toList())
                activePointers.remove(id)
                if (editing) savePositions()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun send(time: Long, action: Int, points: List<Pair<Float, Float>>) {
        val size = targetSize()
        if (size.first <= 0 || size.second <= 0 || displayId() < 0 || points.isEmpty()) return
        val props = Array(points.size) { i -> android.view.MotionEvent.PointerProperties().apply {
            id = i; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }
        val coords = Array(points.size) { i -> android.view.MotionEvent.PointerCoords().apply {
            x = points[i].first * size.first; y = points[i].second * size.second
            pressure = 1f; size = 1f
        } }
        val actionMasked = if (action == MotionEvent.ACTION_MOVE) MotionEvent.ACTION_MOVE else if (action == MotionEvent.ACTION_UP && points.isEmpty()) MotionEvent.ACTION_UP else action
        val event = MotionEvent.obtain(time, time, actionMasked, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { dispatch.send(event, displayId()) } finally { event.recycle() }
    }

    private fun savePositions() {
        val json = JSONObject()
        controls.forEach { c -> json.put(c.label, JSONObject().put("x", c.x.toDouble()).put("y", c.y.toDouble())) }
        prefs.edit().putString("positions", json.toString()).apply()
    }

    fun releaseAll() {
        buttonPointers.clear(); activePointers.clear()
        savePositions()
    }
}
