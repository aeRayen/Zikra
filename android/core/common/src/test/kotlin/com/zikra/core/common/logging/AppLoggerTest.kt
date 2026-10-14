package com.zikra.core.common.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {

    private class BufferSink : LogSink {
        val lines = mutableListOf<Triple<LogLevel, String, String>>()
        override fun write(level: LogLevel, tag: String, message: String) {
            lines += Triple(level, tag, message)
        }
    }

    @Test
    fun messagesAreRedactedBeforeReachingSinks() {
        val sink = BufferSink()
        AppLogger(listOf(sink)).i("Auth") { "signed in as someone@example.com" }
        assertEquals(1, sink.lines.size)
        assertFalse(sink.lines.single().third.contains("someone@example.com"))
    }

    @Test
    fun throwableMessageIsRedactedToo() {
        val sink = BufferSink()
        AppLogger(listOf(sink)).e("Sync", IllegalStateException("bad token=abc123")) { "push failed" }
        val line = sink.lines.single().third
        assertTrue(line.startsWith("push failed | java.lang.IllegalStateException"))
        assertFalse(line.contains("abc123"))
    }

    @Test
    fun levelsBelowMinimumAreNotEvaluated() {
        val sink = BufferSink()
        var evaluated = false
        AppLogger(listOf(sink), minLevel = LogLevel.WARN).d("Tag") {
            evaluated = true
            "debug"
        }
        assertFalse(evaluated)
        assertTrue(sink.lines.isEmpty())
    }
}
