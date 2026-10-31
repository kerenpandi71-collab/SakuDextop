package moe.n4tsu.sakudextop.lite

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Injects mouse events into the chosen logical display, not the phone display. */
internal class LiteInputDispatcher {
    private val inputManager: Any by lazy {
        check(LitePrivilege.hasShizukuPermission()) {
            "Izinkan SakuDextop Lite di Shizuku untuk mengirim touchpad."
        }
        HiddenApiBypass.addHiddenApiExemptions("")
        val serviceBinder = SystemServiceHelper.getSystemService("input")
        val wrappedBinder = ShizukuBinderWrapper(serviceBinder)
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        stub.getMethod("asInterface", IBinder::class.java).invoke(null, wrappedBinder)
            ?: error("InputManager tidak tersedia")
    }

    private val inputInterface by lazy {
        Class.forName("android.hardware.input.IInputManager")
    }
    private val injectMethod by lazy {
        inputInterface.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
    }
    private val setDisplayMethod by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    fun hover(displayId: Int, x: Float, y: Float) {
        send(displayId, x, y, MotionEvent.ACTION_HOVER_MOVE, SystemClock.uptimeMillis(), 0)
    }

    fun click(displayId: Int, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        send(displayId, x, y, MotionEvent.ACTION_DOWN, downTime, MotionEvent.BUTTON_PRIMARY)
        send(displayId, x, y, MotionEvent.ACTION_UP, downTime, 0)
    }

    fun beginDrag(displayId: Int, x: Float, y: Float, downTime: Long) {
        send(displayId, x, y, MotionEvent.ACTION_DOWN, downTime, MotionEvent.BUTTON_PRIMARY)
    }

    fun drag(displayId: Int, x: Float, y: Float, downTime: Long) {
        send(displayId, x, y, MotionEvent.ACTION_MOVE, downTime, MotionEvent.BUTTON_PRIMARY)
    }

    fun endDrag(displayId: Int, x: Float, y: Float, downTime: Long) {
        send(displayId, x, y, MotionEvent.ACTION_UP, downTime, 0)
    }

    fun scroll(displayId: Int, x: Float, y: Float, amount: Float) {
        send(displayId, x, y, MotionEvent.ACTION_SCROLL, SystemClock.uptimeMillis(), 0, amount)
    }

    private fun send(
        displayId: Int,
        x: Float,
        y: Float,
        action: Int,
        downTime: Long,
        buttonState: Int,
        verticalScroll: Float = 0f
    ) {
        require(displayId > 0) { "Display virtual belum aktif" }
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (buttonState == 0) 0f else 1f
            size = 0.08f
            if (action == MotionEvent.ACTION_SCROLL) {
                setAxisValue(MotionEvent.AXIS_VSCROLL, verticalScroll)
            }
        }
        val event = MotionEvent.obtain(
            downTime,
            now.coerceAtLeast(downTime),
            action,
            1,
            arrayOf(properties),
            arrayOf(coordinates),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0
        )
        try {
            setDisplayMethod.invoke(event, displayId)
            val accepted = injectMethod.invoke(inputManager, event, INJECT_WAIT_FOR_RESULT) as Boolean
            check(accepted) { "Android menolak input pada display $displayId" }
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val INJECT_WAIT_FOR_RESULT = 1
    }
}
