package com.zikra.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AdhkarProgressStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var day = LocalDate.of(2026, 10, 16)
    private val file by lazy { File(tmp.root, AdhkarProgressStore.FILE_NAME) }

    @Test
    fun countsUpToTheRepeatAndSurvivesARestart() = runTest(UnconfinedTestDispatcher()) {
        val firstProcess = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val store = AdhkarProgressStore({ file }, firstProcess) { day }
        repeat(5) { store.count(dhikr = 80, repeat = 3) }
        store.count(dhikr = 81, repeat = 1)
        assertEquals(mapOf(80 to 3, 81 to 1), store.counts.first())
        firstProcess.cancel() // the app is closed (or killed) after the last tap

        val again = AdhkarProgressStore({ file }, backgroundScope) { day }
        assertEquals(mapOf(80 to 3, 81 to 1), again.counts.first())
    }

    @Test
    fun aNewDayStartsFromZero() = runTest(UnconfinedTestDispatcher()) {
        val store = AdhkarProgressStore({ file }, backgroundScope) { day }
        store.count(dhikr = 80, repeat = 3)
        day = day.plusDays(1)
        assertEquals(emptyMap<Int, Int>(), store.counts.first())
        store.count(dhikr = 81, repeat = 3)
        assertEquals(mapOf(81 to 1), store.counts.first())
    }

    @Test
    fun resetClearsOnlyTheGivenAdhkar() = runTest(UnconfinedTestDispatcher()) {
        val store = AdhkarProgressStore({ file }, backgroundScope) { day }
        store.count(dhikr = 80, repeat = 3)
        store.count(dhikr = 90, repeat = 3)
        store.reset(listOf(80))
        assertEquals(mapOf(90 to 1), store.counts.first())
    }

    @Test
    fun damagedFileMeansNothingSaidYet() = runTest(UnconfinedTestDispatcher()) {
        file.writeText("{\"counts\": [")
        val store = AdhkarProgressStore({ file }, backgroundScope) { day }
        assertEquals(emptyMap<Int, Int>(), store.counts.first())
    }
}
