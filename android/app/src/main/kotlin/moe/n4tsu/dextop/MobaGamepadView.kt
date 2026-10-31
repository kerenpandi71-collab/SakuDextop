package moe.n4tsu.dextop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * MOBA overlay intended for a separate controller surface.
 *
 * Target coordinates are pixels on the game display, not on this overlay.
 * The caller must calibrate MobaTouchLayout for the game's current resolution.
 */
internal class MobaGamepadView(
    context: Context,
    private val touchMapper: MobaSkillTouchMapper,
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val layout: MobaTouchLayout,
    private val onAnalogChanged: (x: Float, y: Float) -> Unit = { _, _ -> },
) : View(context) {

    enum class Skill { SKILL_1, SKILL_2, SKILL_3, SPELL }

    data class MobaTouchLayout(
        val joystickCenter: PointF,
        val joystickRadius: Float,
        val skill1: PointF,
        val skill2: PointF,
        val skill3: PointF,
        val spell: PointF,
        /** Maximum target-display drag radius for directional skill aiming. */
        val skillAimRadius: Float,
        /** Maximum overlay drag radius for the analog stick. */
        val analogRadius: Float,
    )

    private enum class Region { NONE, ANALOG, SKILL_1, SKILL_2, SKILL_3, SPELL }
    private data class ActiveTouch(
        val region: Region,
        val downX: Float,
        val downY: Float,
        var lastX: Float,
        var lastY: Float,
    )

    private val density = resources.displayMetrics.density
    private val active = linkedMapOf<Int, ActiveTouch>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val skillCenters = linkedMapOf(
        Region.SKILL_1 to PointF(),
        Region.SKILL_2 to PointF(),
        Region.SKILL_3 to PointF(),
        Region.SPELL to PointF(),
    )
    private var analogCenter = PointF()
    private var analogRadius = 1f
    private var skillRadius = 1f
    private var aimRadius = 1f

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry(w.toFloat(), h.toFloat())
    }

    private fun updateGeometry(w: Float = width.toFloat(), h: Float = height.toFloat()) {
        if (w <= 0f || h <= 0f) return
        analogRadius = minOf(w, h) * .13f
        skillRadius = minOf(w, h) * .085f
        aimRadius = minOf(w, h) * .22f
        analogCenter = PointF(w * .18f, h * .72f)

        // Keep hit targets synchronized with their drawn controls after rotation/resizing.
        skillCenters[Region.SKILL_1] = PointF(w * .72f, h * .72f)
        skillCenters[Region.SKILL_2] = PointF(w * .84f, h * .52f)
        skillCenters[Region.SKILL_3] = PointF(w * .72f, h * .32f)
        skillCenters[Region.SPELL] = PointF(w * .92f, h * .82f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateGeometry()

        drawPad(canvas, analogCenter, analogRadius, "MOVE", active.values.any { it.region == Region.ANALOG })
        drawSkill(canvas, Region.SKILL_1, "1")
        drawSkill(canvas, Region.SKILL_2, "2")
        drawSkill(canvas, Region.SKILL_3, "3")
        drawSkill(canvas, Region.SPELL, "SPELL")
    }

    private fun drawPad(canvas: Canvas, center: PointF, radius: Float, label: String, pressed: Boolean) {
        paint.color = if (pressed) 0xCC526D86.toInt() else 0x99303843.toInt()
        canvas.drawCircle(center.x, center.y, radius, paint)
        paint.color = 0xDDE0E6EF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawCircle(center.x, center.y, radius, paint)
        paint.style = Paint.Style.FILL
        labels.textSize = 11f * density
        canvas.drawText(label, center.x, center.y + labels.textSize * .35f, labels)
    }

    private fun drawSkill(canvas: Canvas, region: Region, label: String) {
        val center = skillCenters.getValue(region)
        val pressed = active.values.any { it.region == region }
        paint.color = if (pressed) 0xEE607D9B.toInt() else 0xAA202832.toInt()
        canvas.drawCircle(center.x, center.y, skillRadius, paint)
        paint.color = 0xFFE5EAF0.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawCircle(center.x, center.y, skillRadius, paint)
        paint.style = Paint.Style.FILL
        labels.textSize = if (label == "SPELL") 9f * density else 15f * density
        canvas.drawText(label, center.x, center.y + labels.textSize * .35f, labels)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A touch can arrive immediately after the overlay is attached, before
        // its first frame has been drawn. Initialize hit geometry synchronously.
        updateGeometry()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                beginPointer(event.getPointerId(event.actionIndex), event.getX(event.actionIndex), event.getY(event.actionIndex))
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                movePointer(event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP ->
                endPointer(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        invalidate()
        return true
    }

    private fun beginPointer(id: Int, x: Float, y: Float) {
        if (active.containsKey(id)) return
        val region = hitRegion(x, y)
        val touch = ActiveTouch(region, x, y, x, y)
        active[id] = touch
        when (region) {
            Region.ANALOG -> {
                if (touchMapper.press(token(id), layout.joystickCenter.x, layout.joystickCenter.y)) {
                    if (!updateAnalog(touch, x, y, token(id))) cancelAll()
                } else {
                    active.remove(id)
                }
            }
            Region.SKILL_1 -> tapSkill(token(id), layout.skill1)
            Region.SKILL_2 -> if (!touchMapper.press(token(id), layout.skill2.x, layout.skill2.y)) active.remove(id)
            Region.SKILL_3 -> tapSkill(token(id), layout.skill3)
            Region.SPELL -> tapSkill(token(id), layout.spell)
            Region.NONE -> active.remove(id)
        }
    }

    private fun tapSkill(token: Int, point: PointF) {
        if (touchMapper.press(token, point.x, point.y)) {
            touchMapper.release(token)
        }
    }

    private fun movePointer(id: Int, x: Float, y: Float) {
        val touch = active[id] ?: return
        touch.lastX = x
        touch.lastY = y
        when (touch.region) {
            Region.ANALOG -> if (!updateAnalog(touch, x, y, token(id))) cancelAll()
            Region.SKILL_2 -> if (!updateSkillAim(touch, x, y, token(id))) cancelAll()
            else -> Unit
        }
    }

    private fun updateAnalog(touch: ActiveTouch, x: Float, y: Float, token: Int): Boolean {
        val dx = x - touch.downX
        val dy = y - touch.downY
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val scale = if (length > analogRadius) analogRadius / length else 1f
        val nx = (dx * scale / analogRadius).coerceIn(-1f, 1f)
        val ny = (dy * scale / analogRadius).coerceIn(-1f, 1f)
        val targetX = (layout.joystickCenter.x + nx * layout.joystickRadius).coerceIn(0f, targetWidth - 1f)
        val targetY = (layout.joystickCenter.y + ny * layout.joystickRadius).coerceIn(0f, targetHeight - 1f)
        if (!touchMapper.move(token, targetX, targetY)) return false
        onAnalogChanged(nx, ny)
        return true
    }

    private fun updateSkillAim(touch: ActiveTouch, x: Float, y: Float, token: Int): Boolean {
        val origin = touch.downX to touch.downY
        var dx = x - origin.first
        var dy = y - origin.second
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val scale = if (length > aimRadius) aimRadius / length else 1f
        dx *= scale
        dy *= scale
        val targetX = (layout.skill2.x + dx / aimRadius * layout.skillAimRadius).coerceIn(0f, targetWidth - 1f)
        val targetY = (layout.skill2.y + dy / aimRadius * layout.skillAimRadius).coerceIn(0f, targetHeight - 1f)
        return touchMapper.move(token, targetX, targetY)
    }

    private fun endPointer(id: Int) {
        val touch = active.remove(id) ?: return
        if (touch.region == Region.ANALOG || touch.region == Region.SKILL_2) {
            touchMapper.release(token(id))
        }
        if (touch.region == Region.ANALOG) onAnalogChanged(0f, 0f)
    }

    private fun cancelAll() {
        active.keys.toList().forEach { touchMapper.release(token(it)) }
        active.clear()
        touchMapper.cancel()
        onAnalogChanged(0f, 0f)
    }

    private fun hitRegion(x: Float, y: Float): Region {
        if (hypot((x - analogCenter.x).toDouble(), (y - analogCenter.y).toDouble()) <= analogRadius * 1.35f) return Region.ANALOG
        return skillCenters.entries.firstOrNull { (_, p) ->
            hypot((x - p.x).toDouble(), (y - p.y).toDouble()) <= skillRadius * 1.45f
        }?.key ?: Region.NONE
    }

    private fun token(id: Int) = 1000 + id
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
