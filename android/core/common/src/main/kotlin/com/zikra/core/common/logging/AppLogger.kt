package com.zikra.core.common.logging

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Destination of already-redacted log lines (Logcat, crash reporter, test buffer). */
fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String)
}

/**
 * The only logger the app uses. Every message and throwable description passes through
 * [Redactor] before reaching any [LogSink]. Messages are built lazily so disabled levels cost nothing.
 */
class AppLogger(
    private val sinks: List<LogSink>,
    private val minLevel: LogLevel = LogLevel.INFO,
) {
    fun d(tag: String, message: () -> String) = log(LogLevel.DEBUG, tag, null, message)

    fun i(tag: String, message: () -> String) = log(LogLevel.INFO, tag, null, message)

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) =
        log(LogLevel.WARN, tag, throwable, message)

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) =
        log(LogLevel.ERROR, tag, throwable, message)

    private fun log(level: LogLevel, tag: String, throwable: Throwable?, message: () -> String) {
        if (level < minLevel || sinks.isEmpty()) return
        val text = buildString {
            append(message())
            if (throwable != null) {
                // Class name + message only; stack traces go to the crash reporter, which scrubs separately.
                append(" | ").append(throwable::class.java.name)
                throwable.message?.let { append(": ").append(it) }
            }
        }
        val safe = Redactor.redact(text)
        sinks.forEach { it.write(level, tag, safe) }
    }
}
