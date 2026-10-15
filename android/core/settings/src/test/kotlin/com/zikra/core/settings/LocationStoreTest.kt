package com.zikra.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LocationStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val makkah = SavedLocation(LocationSource.CITY, 104515, 21.42664, 39.82563, "Asia/Riyadh", 1_000)

    private fun TestScope.store(file: File) = LocationStore({ file }, backgroundScope)

    @Test
    fun startsEmptyThenRoundTrips() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, LocationStore.FILE_NAME)
        val store = store(file)
        assertNull(store.location.first())
        store.save(makkah)
        assertEquals(makkah, store.location.first())
        store.clear()
        assertNull(store.location.first())
    }

    @Test
    fun survivesProcessRestart() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, LocationStore.FILE_NAME)
        val firstProcess = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        LocationStore({ file }, firstProcess).save(makkah)
        firstProcess.cancel() // the process ends; only then may another instance own the file
        assertEquals(makkah, store(file).location.first())
    }

    @Test
    fun corruptFileFallsBackToNoLocationInsteadOfCrashing() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, LocationStore.FILE_NAME).apply { writeText("{not json") }
        val store = store(file)
        assertNull(store.location.first())
        store.save(makkah)
        assertEquals(makkah, store.location.first())
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() = runTest(UnconfinedTestDispatcher()) {
        val file = File(tmp.root, LocationStore.FILE_NAME)
        file.writeText(
            """{"version":2,"extra":true,"location":{"source":"device","cityId":null,"latitude":1.5,""" +
                """"longitude":2.5,"timeZone":"UTC","savedAtEpochMs":5,"accuracy":3}}""",
        )
        val loc = store(file).location.first()
        assertEquals(SavedLocation(LocationSource.DEVICE, null, 1.5, 2.5, "UTC", 5), loc)
    }
}
