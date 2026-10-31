package moe.n4tsu.dextop

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/**
 * Injects a real multi-pointer touch stream into the game display.
 *
 * The mapper deliberately knows nothing about the visual gamepad layout.
 * Callers translate control positions into coordinates in the target display:
 * press() -> move() zero or more times -> release(), or cancel().
 *
 * All coordinates must already be expressed in target-display pixels.
 */
internal class MobaSkillTouchMapper(
    private val inputDispatcher: InputDispatcher,
    private val targetDisplayId: Int,
    private val eventSource: Int = InputDevice.SOURCE_TOUCHSCREEN,
) {
    private data class Pointer(
        val pointerId: Int,
        var x: Float,
        var y: Float,
    )

    private val pointers = linkedMapOf<Int, Pointer>()
    private var downTime = 0L

    /**
     * Starts a touch for a logical control token (for example, skill-2).
     * Multiple distinct tokens may stay pressed and move simultaneously.
     */
    @Synchronized
    fun press(controlToken: Int, x: Float, y: Float): Boolean {
        if (pointers.containsKey(controlToken) || pointers.size >= MAX_POINTERS) return false
        if (!x.isFinite() || !y.isFinite()) return false

        val pointerId = (0 until MAX_POINTERS).firstOrNull { candidate ->
            pointers.values.none { it.pointerId == candidate }
        } ?: return false

        val wasEmpty = pointers.isEmpty()
        val nextDownTime = if (wasEmpty) SystemClock.uptimeMillis() else downTime
        val actionIndex = pointers.size
        val action = if (wasEmpty) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

        val next = Pointer(pointerId, x, y)
        val snapshot = pointers.values.toList() + next
        val accepted = dispatch(action, snapshot, nextDownTime)
        if (accepted) {
            if (wasEmpty) downTime = nextDownTime
            pointers[controlToken] = next
        }
        return accepted
    }

    /** Moves one active control while preserving every other active pointer. */
    @Synchronized
    fun move(controlToken: Int, x: Float, y: Float): Boolean {
        val pointer = pointers[controlToken] ?: return false
        if (!x.isFinite() || !y.isFinite()) return false
        val previousX = pointer.x
        val previousY = pointer.y
        pointer.x = x
        pointer.y = y
        val accepted = dispatch(MotionEvent.ACTION_MOVE, pointers.values.toList(), downTime)
        if (!accepted) {
            pointer.x = previousX
            pointer.y = previousY
            cancel()
        }
        return accepted
    }

    /** Ends one control without releasing any other active control. */
    @Synchronized
    fun release(controlToken: Int): Boolean {
        val pointer = pointers[controlToken] ?: return false
        val snapshot = pointers.values.toList()
        val actionIndex = snapshot.indexOfFirst { it.pointerId == pointer.pointerId }
        val action = if (snapshot.size == 1) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

        val accepted = dispatch(action, snapshot, downTime)
        if (accepted) {
            pointers.remove(controlToken)
            if (pointers.isEmpty()) downTime = 0L
        } else {
            // Never leave an uncertain partially injected gesture active.
            cancel()
        }
        return accepted
    }

    /** Cancels the whole gesture. Use this when the overlay/game loses focus. */
    @Synchronized
    fun cancel(): Boolean {
        if (pointers.isEmpty()) return true
        val accepted = dispatch(MotionEvent.ACTION_CANCEL, pointers.values.toList(), downTime)
        pointers.clear()
        downTime = 0L
        return accepted
    }

    @Synchronized
    fun activePointerCount(): Int = pointers.size

    private fun dispatch(action: Int, active: List<Pointer>, gestureDownTime: Long): Boolean {
        if (active.isEmpty()) return false
        val properties = Array(active.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = active[index].pointerId
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coordinates = Array(active.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = active[index].x
                y = active[index].y
                pressure = 1f
                size = 1f
            }
        }
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            gestureDownTime,
            now,
            action,
            active.size,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            eventSource,
            0,
        )
        return try {
            inputDispatcher.send(event, targetDisplayId)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val MAX_POINTERS = 10
    }
}
