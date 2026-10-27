package moe.n4tsu.dextop.plasma

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * The desktop layer of the Dextop window manager.
 *
 * It is started as a secondary HOME activity on the Dextop display so the
 * framework keeps it underneath every application task (tapping HOME never
 * raises it over windows).  It shows the wallpaper and forwards desktop
 * clicks to [PlasmaShell].
 */
class PlasmaDesktopActivity : Activity() {
    private var desktop: DesktopView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val view = DesktopView(this)
        desktop = view
        setContentView(view)
        // Samsung's PhoneWindow does not have a DecorView-backed insets
        // controller until the content view has been installed. Calling
        // Window.getInsetsController() before this point crashes during HOME
        // activity creation on the physical display.
        window.decorView.windowInsetsController?.let { controller ->
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        applySettings()
        // Never survive on a display the running shell does not own (for
        // example the phone screen, if HOME was redirected there).
        if (PlasmaShell.current?.onDesktopActivityCreated(this) != true) finish()
    }

    override fun onDestroy() {
        PlasmaShell.current?.onDesktopActivityDestroyed(this)
        super.onDestroy()
    }

    internal fun applySettings() {
        // Use Android's current system wallpaper instead of drawing a custom
        // Plasma-style wallpaper. The activity remains the desktop background
        // layer; window management and input handling stay in the shell.
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        desktop?.drawWallpaper = false
        desktop?.invalidate()
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        PlasmaShell.current?.onDesktopKey(event) == true || super.onKeyDown(keyCode, event)

    private class DesktopView(context: Context) : View(context) {
        var drawWallpaper = true
        private var wallpaper: Bitmap? = null
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                PlasmaShell.current?.onDesktopClicked()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                PlasmaShell.current?.showDesktopMenu(e.rawX, e.rawY)
            }
        })

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            wallpaper?.recycle()
            wallpaper = if (w > 0 && h > 0) PlasmaWallpaper.render(w, h) else null
        }

        override fun onDraw(canvas: Canvas) {
            if (drawWallpaper) wallpaper?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
                PlasmaShell.current?.showDesktopMenu(event.rawX, event.rawY)
                return true
            }
            return detector.onTouchEvent(event)
        }
    }
}

