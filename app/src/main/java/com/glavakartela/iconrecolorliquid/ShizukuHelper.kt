package com.glavakartela.iconrecolorliquid

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import kotlin.system.exitProcess

/** Runs shell commands in a process started by Shizuku. */
class UserService : IUserService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.trim()
    } catch (e: Exception) {
        "error: ${e.message}"
    }
}

/** Connects to Shizuku and runs commands with shell privileges (like adb). */
object ShizukuHelper {
    private const val REQUEST_CODE = 1001

    @Volatile
    private var service: IUserService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IUserService.Stub.asInterface(binder)
            } else {
                null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun args(ctx: Context) =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, UserService::class.java.name))
            .processNameSuffix("shell")
            .daemon(false)
            .version(1)

    fun isRunning(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean =
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun isConnected(): Boolean = service != null

    fun connect(ctx: Context): String {
        if (!isRunning()) {
            return ctx.getString(R.string.shizuku_not_running)
        }
        if (!hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
            return ctx.getString(R.string.shizuku_grant_permission)
        }
        if (service == null) Shizuku.bindUserService(args(ctx), connection)
        return ctx.getString(R.string.shizuku_connecting)
    }

    fun exec(ctx: Context, command: String): String =
        service?.exec(command) ?: ctx.getString(R.string.shizuku_service_missing)

    fun grantShortcutPermission(ctx: Context): String {
        val pkg = ctx.packageName
        val out = exec(ctx, "appops set $pkg 10017 allow; appops get $pkg 10017")
        return out.ifBlank { ctx.getString(R.string.done) }
    }

    fun grantAllFilesAccess(ctx: Context): String {
        val pkg = ctx.packageName
        return exec(
            ctx,
            "appops set --uid $pkg MANAGE_EXTERNAL_STORAGE allow; " +
                "appops set $pkg MANAGE_EXTERNAL_STORAGE allow; " +
                "sleep 1; am start -n $pkg/$pkg.MainActivity",
        )
    }
}
