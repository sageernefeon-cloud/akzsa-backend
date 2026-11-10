package com.nefeon.akzsa.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Self-hosted crash logging (the alternative P5-2 explicitly allows instead of Firebase
 * Crashlytics). Crashlytics needs a Firebase project + google-services.json before the build
 * will even compile — applying its Gradle plugin without that file present is a hard build
 * failure, not a graceful no-op. This avoids that: it works offline, with zero account setup,
 * from the moment the app is installed.
 *
 * It catches uncaught exceptions and writes a redacted stack trace to app-private storage.
 * [recordHandled] additionally lets a try/catch block in the IME or a worker log something it
 * recovered from, without crashing the process. Swap install()/recordHandled() for Crashlytics
 * calls later if you set up a Firebase project — this file is the one place that would change.
 */
object CrashReporter {
    private const val DIR_NAME = "crash_logs"
    private const val MAX_LOGS = 20

    /** Call once, as early as possible (Application.onCreate). Safe to call more than once. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable, tag = "fatal")
            } catch (_: Throwable) {
                // Never let the crash logger itself cause a second crash.
            }
            existing?.uncaughtException(thread, throwable)
        }
    }

    /** For a caught exception you recovered from but still want on record (e.g. onCreate/onStartInputView/doWork). */
    fun recordHandled(context: Context, tag: String, throwable: Throwable) {
        try {
            writeCrashLog(context.applicationContext, Thread.currentThread(), throwable, tag = tag)
        } catch (_: Throwable) {
            // Best-effort; the logger must never throw from inside a recovery path.
        }
    }

    /** Most recent crash/handled-error log files, newest first — for a future "Send logs" settings action. */
    fun recentLogs(context: Context, limit: Int = MAX_LOGS): List<File> =
        crashDir(context.applicationContext)
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?: emptyList()

    private fun crashDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable, tag: String) {
        val dir = crashDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val file = File(dir, "crash-$tag-$stamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        // The stack trace is code/class names, not user-typed text, so it needs no redaction —
        // but an exception message could in theory echo back something the user typed, so it
        // goes through the same Redactor used for diagnostics uploads, defensively.
        val redactedMessage = Redactor.redact(throwable.message ?: "")

        file.writeText(
            buildString {
                appendLine("time: $stamp")
                appendLine("tag: $tag")
                appendLine("thread: ${thread.name}")
                appendLine("type: ${throwable.javaClass.name}")
                appendLine("message: $redactedMessage")
                appendLine("---")
                append(sw.toString())
            }
        )

        // Keep only the most recent MAX_LOGS files so this never grows without bound.
        val all = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        all.drop(MAX_LOGS).forEach { it.delete() }
    }
}
