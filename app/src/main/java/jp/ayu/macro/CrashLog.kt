package jp.ayu.macro

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v0.9.1: 端末の中だけに残すクラッシュログ。
 * - 最後に落ちたときのスタックトレースをアプリ専用の領域（filesDir）に1件だけ保存する
 * - 送信はしない（INTERNET 権限なし）。次に開いたとき「ログをコピー」で利用者が自分で貼り付けられる
 * - 他のアプリの情報や操作の中身は含めない（このアプリのバージョン・Android のバージョン・機種名・例外だけ）
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    @Volatile private var installed = false

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext ?: ctx
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            if (previous != null) previous.uncaughtException(t, e) else {
                android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10)
            }
        }
    }

    fun format(ctx: Context, thread: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            append("操作マクロ / Macro Recorder ").append(version).append('\n')
            append("time: ").append(time).append('\n')
            append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("thread: ").append(thread.name).append("\n\n")
            append(sw.toString().take(60_000))
        }
    }

    fun write(ctx: Context, thread: Thread, e: Throwable) {
        File(ctx.filesDir, FILE).writeText(format(ctx, thread, e))
    }

    fun read(ctx: Context): String? = runCatching {
        File(ctx.filesDir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear(ctx: Context) { runCatching { File(ctx.filesDir, FILE).delete() } }
}

/** v0.9.1: プロセスの起動直後にクラッシュログの記録を始める（Activity より前に落ちても残るように） */
class MacroApp : Application() {
    override fun onCreate() {
        CrashLog.install(this)
        super.onCreate()
    }
}
