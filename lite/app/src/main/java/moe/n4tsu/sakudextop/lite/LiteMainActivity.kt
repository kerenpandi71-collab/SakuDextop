package moe.n4tsu.sakudextop.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class LiteMainActivity : Activity() {
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val inputWorker: ExecutorService = Executors.newSingleThreadExecutor()
    private val inputDispatcher = LiteInputDispatcher()
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }

    private lateinit var status: TextView
    private lateinit var privilegeStatus: TextView
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var densityInput: EditText
    private lateinit var touchpad: LiteTouchpadView

    @Volatile private var activeDisplayId = Display.INVALID_DISPLAY
    @Volatile private var activeSpec: String? = null
    @Volatile private var displayWidth = 820
    @Volatile private var displayHeight = 1320
    @Volatile private var cursorX = 410f
    @Volatile private var cursorY = 660f
    @Volatile private var dragDownTime = 0L

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            updatePrivilegeStatus()
            showMessage(
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    "Izin Shizuku diberikan. Touchpad siap digunakan."
                } else {
                    "Izin Shizuku belum diberikan."
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 17, 20)
        window.navigationBarColor = Color.rgb(16, 17, 20)
        buildInterface()
        updatePrivilegeStatus()
        restoreDisplay()
    }

    override fun onStart() {
        super.onStart()
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
    }

    override fun onStop() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
        super.onStop()
    }

    private fun buildInterface() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(16, 17, 20))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        page.addView(label("SAKU DEXTOP", 22f, Color.WHITE, true))
        page.addView(label("L I T E   •   VIRTUAL DISPLAY + TOUCHPAD", 11f, 0xFF8AB4F8.toInt(), true, dp(7)))
        page.addView(label("Hanya dua fungsi inti. Tanpa desktop shell, Linux, atau gamepad.", 13f, 0xFFABB3BF.toInt(), false, dp(12)))

        privilegeStatus = label("Memeriksa akses root / Shizuku…", 12f, 0xFFABB3BF.toInt(), false, dp(8))
        page.addView(privilegeStatus)

        val sizeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        widthInput = numberField("Lebar", "820")
        heightInput = numberField("Tinggi", "1320")
        densityInput = numberField("DPI", "320")
        sizeRow.addView(widthInput, weightedParams())
        sizeRow.addView(heightInput, weightedParams())
        sizeRow.addView(densityInput, weightedParams())
        page.addView(sizeRow)

        val createButton = button("BUAT VIRTUAL DISPLAY")
        createButton.setOnClickListener { createVirtualDisplay(createButton) }
        page.addView(createButton, buttonParams(dp(14)))

        val openAppButton = button("BUKA APLIKASI DI DISPLAY")
        openAppButton.setOnClickListener { showAppPicker() }
        page.addView(openAppButton, buttonParams(dp(8)))

        val stopButton = button("MATIKAN VIRTUAL DISPLAY").apply {
            setTextColor(0xFFFFA5A5.toInt())
            setOnClickListener { stopVirtualDisplay(this) }
        }
        page.addView(stopButton, buttonParams(dp(8)))

        status = label("Virtual display belum aktif.", 13f, 0xFFB9C2CE.toInt(), false, dp(14))
        page.addView(status)

        page.addView(label("TOUCHPAD", 16f, Color.WHITE, true, dp(18)))
        page.addView(label(
            "Gerakkan satu jari untuk memindahkan pointer. Ketuk untuk klik, tahan lalu geser untuk drag, dua jari untuk scroll.",
            12f, 0xFFABB3BF.toInt(), false, dp(5)
        ))

        touchpad = LiteTouchpadView(this).apply {
            listener = object : LiteTouchpadView.Listener {
                override fun move(dx: Float, dy: Float) = movePointer(dx, dy, dragging = false)
                override fun tap() = sendClick()
                override fun dragStart() {
                    dragDownTime = SystemClock.uptimeMillis()
                    enqueueInput { beginDrag(activeDisplayId, cursorX, cursorY, dragDownTime) }
                }
                override fun dragMove(dx: Float, dy: Float) = movePointer(dx, dy, dragging = true)
                override fun dragEnd() {
                    val id = activeDisplayId
                    val x = cursorX
                    val y = cursorY
                    val down = dragDownTime
                    enqueueInput { endDrag(id, x, y, down) }
                }
                override fun scroll(amount: Float) {
                    val id = activeDisplayId
                    val x = cursorX
                    val y = cursorY
                    enqueueInput { scroll(id, x, y, amount) }
                }
            }
        }
        page.addView(touchpad, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(250)
        ).apply { topMargin = dp(10) })

        val shizukuButton = button("SAMBUNGKAN / IZINKAN SHIZUKU")
        shizukuButton.setOnClickListener {
            runCatching {
                LitePrivilege.requestShizukuPermission(this, SHIZUKU_REQUEST_CODE)
            }.onFailure { showMessage(it.message ?: "Shizuku belum siap") }
            updatePrivilegeStatus()
        }
        page.addView(shizukuButton, buttonParams(dp(12)))
        page.addView(label(
            "Catatan: root dapat membuat display. Shizuku dengan izin diperlukan untuk injeksi pointer langsung ke display tujuan.",
            11f, 0xFF7F8997.toInt(), false, dp(9)
        ))

        setContentView(scroll)
    }

    private fun createVirtualDisplay(button: Button) {
        val width = widthInput.text.toString().toIntOrNull()
        val height = heightInput.text.toString().toIntOrNull()
        val density = densityInput.text.toString().toIntOrNull()
        if (width == null || height == null || density == null ||
            width !in 320..4096 || height !in 320..4096 || density !in 120..640
        ) {
            showMessage("Ukuran atau DPI tidak valid.")
            return
        }
        val spec = "${width}x${height}/${density}"
        button.isEnabled = false
        setStatus("Membuat virtual display ${spec}…")
        worker.execute {
            runCatching {
                val before = displayManager.displays.mapTo(mutableSetOf()) { it.displayId }
                val current = readOverlaySetting()
                val entries = current.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                if (spec !in entries) {
                    val updated = (entries + spec).joinToString(";")
                    val result = LitePrivilege.execute("settings", "put", "global", OVERLAY_SETTING, updated)
                    check(result.succeeded) { result.output.ifBlank { "Gagal meminta virtual display." } }
                }
                var found: Display? = null
                repeat(60) {
                    found = findOverlayDisplay(before)
                    if (found != null) return@repeat
                    Thread.sleep(100L)
                }
                val target = found ?: error("Virtual display tidak muncul. Periksa dukungan overlay display pada ROM.")
                preferences.edit()
                    .putInt(KEY_DISPLAY_ID, target.displayId)
                    .putString(KEY_SPEC, spec)
                    .apply()
                target
            }.onSuccess { target ->
                runOnUiThread {
                    button.isEnabled = true
                    activateDisplay(target, spec)
                    setStatus("Aktif • Display ID ${target.displayId} • ${displayWidth}×${displayHeight}")
                }
            }.onFailure { error ->
                runOnUiThread {
                    button.isEnabled = true
                    setStatus("Gagal: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun stopVirtualDisplay(button: Button) {
        val spec = activeSpec ?: preferences.getString(KEY_SPEC, null)
        if (spec.isNullOrBlank()) {
            showMessage("Tidak ada virtual display milik Lite yang tercatat.")
            return
        }
        button.isEnabled = false
        setStatus("Mematikan virtual display…")
        worker.execute {
            runCatching {
                val entries = readOverlaySetting().split(';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != spec }
                val result = if (entries.isEmpty()) {
                    LitePrivilege.execute("settings", "delete", "global", OVERLAY_SETTING)
                } else {
                    LitePrivilege.execute("settings", "put", "global", OVERLAY_SETTING, entries.joinToString(";"))
                }
                check(result.succeeded) { result.output.ifBlank { "Gagal menghapus spesifikasi display." } }
                repeat(40) {
                    if (displayManager.getDisplay(activeDisplayId) == null) return@repeat
                    Thread.sleep(100L)
                }
            }.onSuccess {
                runOnUiThread {
                    button.isEnabled = true
                    activeDisplayId = Display.INVALID_DISPLAY
                    activeSpec = null
                    preferences.edit().remove(KEY_DISPLAY_ID).remove(KEY_SPEC).apply()
                    setStatus("Virtual display dimatikan.")
                    updatePrivilegeStatus()
                }
            }.onFailure { error ->
                runOnUiThread {
                    button.isEnabled = true
                    setStatus("Gagal mematikan display: ${error.message}")
                }
            }
        }
    }

    private fun restoreDisplay() {
        val savedId = preferences.getInt(KEY_DISPLAY_ID, Display.INVALID_DISPLAY)
        val savedSpec = preferences.getString(KEY_SPEC, null)
        val existing = displayManager.getDisplay(savedId)
        if (existing != null && isOverlay(existing) && !savedSpec.isNullOrBlank()) {
            activateDisplay(existing, savedSpec)
            return
        }
        if (savedId != Display.INVALID_DISPLAY) {
            preferences.edit().remove(KEY_DISPLAY_ID).remove(KEY_SPEC).apply()
        }
    }

    private fun activateDisplay(display: Display, spec: String) {
        activeDisplayId = display.displayId
        activeSpec = spec
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        displayWidth = size.x.takeIf { it > 0 } ?: spec.substringBefore('x').toInt()
        displayHeight = size.y.takeIf { it > 0 } ?: spec.substringAfter('x').substringBefore('/').toInt()
        cursorX = displayWidth / 2f
        cursorY = displayHeight / 2f
        setStatus("Aktif • Display ID ${display.displayId} • ${displayWidth}×${displayHeight}")
    }

    private fun showAppPicker() {
        val id = activeDisplayId
        if (id == Display.INVALID_DISPLAY) {
            showMessage("Buat virtual display terlebih dahulu.")
            return
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (activity.packageName == packageName) return@mapNotNull null
                AppEntry(
                    activity.packageName,
                    activity.applicationInfo.loadLabel(packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            showMessage("Tidak ada aplikasi yang bisa dibuka.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Buka aplikasi di Display $id")
            .setItems(apps.map { it.label }.toTypedArray()) { _, selected ->
                val entry = apps[selected]
                worker.execute {
                    val result = LitePrivilege.execute(
                        "am", "start", "--display", id.toString(),
                        "-a", Intent.ACTION_MAIN,
                        "-c", Intent.CATEGORY_LAUNCHER,
                        "-p", entry.packageName
                    )
                    runOnUiThread {
                        if (result.succeeded) {
                            setStatus("Membuka ${entry.label} di Display $id.")
                        } else {
                            setStatus("Gagal membuka aplikasi: ${result.output}")
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun readOverlaySetting(): String {
        val result = LitePrivilege.execute("settings", "get", "global", OVERLAY_SETTING)
        check(result.succeeded) { result.output.ifBlank { "Tidak bisa membaca pengaturan overlay display." } }
        return result.output.trim().takeUnless { it == "null" } ?: ""
    }

    private fun findOverlayDisplay(previousIds: Set<Int>): Display? {
        return displayManager.displays.firstOrNull {
            it.displayId !in previousIds && it.displayId != Display.DEFAULT_DISPLAY && isOverlay(it)
        } ?: displayManager.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && isOverlay(it)
        }
    }

    private fun isOverlay(display: Display): Boolean {
        val type = runCatching {
            Display::class.java.getMethod("getType").invoke(display) as Int
        }.getOrNull()
        return type == TYPE_OVERLAY || display.name.contains("overlay", ignoreCase = true)
    }

    private fun movePointer(dx: Float, dy: Float, dragging: Boolean) {
        val id = activeDisplayId
        if (id == Display.INVALID_DISPLAY) return
        val gain = 1.85f
        cursorX = (cursorX + dx * gain).coerceIn(0f, (displayWidth - 1).coerceAtLeast(1).toFloat())
        cursorY = (cursorY + dy * gain).coerceIn(0f, (displayHeight - 1).coerceAtLeast(1).toFloat())
        val x = cursorX
        val y = cursorY
        val down = dragDownTime
        enqueueInput {
            if (dragging) drag(id, x, y, down) else hover(id, x, y)
        }
    }

    private fun sendClick() {
        val id = activeDisplayId
        if (id == Display.INVALID_DISPLAY) return
        val x = cursorX
        val y = cursorY
        enqueueInput { click(id, x, y) }
    }

    private fun enqueueInput(action: LiteInputDispatcher.() -> Unit) {
        if (activeDisplayId == Display.INVALID_DISPLAY) return
        inputWorker.execute {
            runCatching { inputDispatcher.action() }
                .onFailure { error ->
                    runOnUiThread {
                        setStatus("Touchpad gagal: ${error.message ?: error.javaClass.simpleName}")
                    }
                }
        }
    }

    private fun updatePrivilegeStatus() {
        val root = LitePrivilege.execute("id")
        val rootReady = root.succeeded && root.output.contains("uid=0")
        val shizukuReady = LitePrivilege.hasShizukuPermission()
        privilegeStatus.text = when {
            shizukuReady && rootReady -> "Akses siap • Root + Shizuku diizinkan"
            shizukuReady -> "Akses siap • Shizuku diizinkan"
            rootReady -> "Root siap • Izinkan Shizuku untuk touchpad lintas display"
            LitePrivilege.shizukuConnected() -> "Shizuku aktif • Izin aplikasi belum diberikan"
            else -> "Akses belum siap • Root atau Shizuku diperlukan"
        }
        privilegeStatus.setTextColor(if (rootReady || shizukuReady) 0xFF8ED6A5.toInt() else 0xFFFFC078.toInt())
    }

    private fun setStatus(message: String) {
        status.text = message
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean,
        topMargin: Int = 0
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, topMargin, 0, dp(3))
    }

    private fun numberField(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 14f
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF7F8997.toInt())
        setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setPadding(dp(10), dp(9), dp(10), dp(9))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF20242C.toInt())
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), 0xFF39414D.toInt())
        }
    }

    private fun button(value: String) = Button(this).apply {
        text = value
        textSize = 12f
        isAllCaps = false
        minHeight = dp(46)
        setTextColor(Color.WHITE)
    }

    private fun weightedParams() = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        marginEnd = dp(5)
    }

    private fun buttonParams(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
    ).apply { topMargin = top }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroy() {
        worker.shutdownNow()
        inputWorker.shutdownNow()
        super.onDestroy()
    }

    private data class AppEntry(val packageName: String, val label: String)

    private companion object {
        const val PREFERENCES = "saku_dextop_lite"
        const val KEY_DISPLAY_ID = "display_id"
        const val KEY_SPEC = "display_spec"
        const val OVERLAY_SETTING = "overlay_display_devices"
        const val TYPE_OVERLAY = 4
        const val SHIZUKU_REQUEST_CODE = 200
    }
}
