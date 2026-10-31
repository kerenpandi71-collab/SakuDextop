package moe.n4tsu.sakudextop.lite

import android.app.Activity
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Runs the small set of display-management commands as shell. Root is preferred;
 * Shizuku is used when root is unavailable.
 */
internal object LitePrivilege {
    data class Result(val code: Int, val output: String) {
        val succeeded: Boolean get() = code == 0
    }

    fun hasShizukuPermission(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shizukuConnected(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun requestShizukuPermission(activity: Activity, requestCode: Int) {
        check(shizukuConnected()) { "Shizuku belum berjalan. Jalankan Shizuku terlebih dahulu." }
        if (!hasShizukuPermission()) Shizuku.requestPermission(requestCode)
    }

    fun execute(vararg arguments: String): Result {
        require(arguments.isNotEmpty())
        val rootResult = runCatching { executeAsRoot(arguments) }.getOrNull()
        if (rootResult?.succeeded == true) return rootResult

        if (shizukuConnected() && hasShizukuPermission()) {
            return runCatching { executeWithShizuku(arguments) }
                .getOrElse { Result(-1, it.message ?: "Perintah Shizuku gagal") }
        }

        return rootResult ?: Result(-1, "Root tidak tersedia. Jalankan Shizuku dan berikan izin kepada SakuDextop Lite.")
    }

    private fun executeAsRoot(arguments: Array<out String>): Result {
        val command = arguments.joinToString(" ") { shellQuote(it) }
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return Result(-1, "Permintaan root timeout")
        }
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        return Result(process.exitValue(), output)
    }

    private fun executeWithShizuku(arguments: Array<out String>): Result {
        val binder = Shizuku.getBinder() ?: error("Binder Shizuku tidak tersedia")
        val process = IShizukuService.Stub.asInterface(binder).newProcess(arguments, null, null)
        process.outputStream.close()
        val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            .bufferedReader().use { it.readText().trim() }
        val error = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
            .bufferedReader().use { it.readText().trim() }
        val exitCode = process.waitFor()
        return Result(exitCode, output.ifBlank { error })
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
