package com.zikra.platform.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlarmRegistryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun registry() = AlarmRegistry(File(tmp.root, "alarms/registry.json"))

    @Test
    fun requestCodesAreUniqueStableAndNeverReused() {
        val r = registry()
        r.replace(AlarmKind.ADHAN, listOf("a" to 10L, "b" to 20L))
        val first = r.entries().associate { it.key to it.requestCode }
        assertEquals(2, first.values.toSet().size)
        r.replace(AlarmKind.ADHAN, listOf("a" to 11L, "c" to 30L))
        val second = r.entries().associate { it.key to it.requestCode }
        assertEquals(first["a"], second["a"])
        // «c» never takes «b»'s old code: a late-delivered PendingIntent can't be mistaken for another alarm.
        assertTrue(second["c"]!! > first.values.max())
    }

    @Test
    fun survivesReopeningAndPrunesThePast() {
        registry().replace(AlarmKind.ADHAN, listOf("old" to 5L, "new" to 50L))
        val reopened = registry()
        assertEquals(listOf("new"), reopened.pruneBefore(10L).map { it.key })
        assertEquals(listOf("new"), registry().entries().map { it.key })
    }

    @Test
    fun damagedFileStartsEmptyInsteadOfCrashing() {
        File(tmp.root, "alarms").mkdirs()
        File(tmp.root, "alarms/registry.json").writeText("{broken")
        val r = registry()
        assertTrue(r.entries().isEmpty())
        r.replace(AlarmKind.ADHAN, listOf("a" to 1L))
        assertEquals(1, r.entries().size)
    }

    @Test
    fun duplicateKeysKeepTheFirstTime() {
        val r = registry()
        r.replace(AlarmKind.ADHAN, listOf("a" to 1L, "a" to 2L))
        assertEquals(listOf(1L), r.entries().map { it.triggerAtEpochMs })
    }
}
