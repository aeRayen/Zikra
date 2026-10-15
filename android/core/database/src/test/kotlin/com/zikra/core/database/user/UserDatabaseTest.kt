package com.zikra.core.database.user

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDatabaseTest {

    private lateinit var db: UserDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun bookmark(id: String, key: String = "quran:2:255") =
        BookmarkEntity(id = id, itemType = "quran_ayah", itemKey = key, createdAt = 1, clientUpdatedAt = 1)

    @Test
    fun deletedBookmarksStayAsTombstonesButAreHidden() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark("b1"))
        dao.markDeleted("b1", now = 2)

        assertTrue(dao.observeLive().first().isEmpty())
        val tombstone = dao.find("quran_ayah", "quran:2:255")!!
        assertTrue(tombstone.deleted && tombstone.dirty)
    }

    @Test
    fun reAddingADeletedBookmarkRevivesTheSameRow() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark("b1"))
        dao.markDeleted("b1", now = 2)
        dao.upsert(dao.find("quran_ayah", "quran:2:255")!!.copy(deleted = false, clientUpdatedAt = 3))

        assertEquals(listOf("b1"), dao.observeLive().first().map { it.id })
    }

    /** Room @Upsert resolves a unique-index conflict by updating by primary key, so a second id
     *  for the same item is silently ignored: never a duplicate row. Repositories look up by key first. */
    @Test
    fun theSameItemNeverGetsASecondRow() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark("b1"))
        dao.upsert(bookmark("b2"))
        assertEquals(listOf("b1"), dao.observeLive().first().map { it.id })
    }

    @Test
    fun deletingABookmarkRemovesItsLocalNote() = runTest {
        val dao = db.bookmarkDao()
        dao.upsert(bookmark("b1"))
        dao.upsertNote(BookmarkNoteEntity(bookmarkId = "b1", text = "note", updatedAt = 1))
        db.openHelper.writableDatabase.execSQL("DELETE FROM bookmark WHERE id = 'b1'")
        assertNull(dao.note("b1"))
    }

    @Test
    fun oneProgressRowPerGoalAndDayAndCascadeOnGoalDelete() = runTest {
        val dao = db.worshipDao()
        dao.upsertGoal(WorshipGoalEntity("g1", "quran_pages", null, 5, "daily", clientUpdatedAt = 1))
        dao.upsertProgress(WorshipProgressEntity("p1", "g1", "2026-10-15", value = 2, clientUpdatedAt = 1))
        dao.upsertProgress(WorshipProgressEntity("p1", "g1", "2026-10-15", value = 4, clientUpdatedAt = 2))
        assertEquals(4, dao.progress("g1", "2026-10-15")!!.value)
        dao.upsertProgress(WorshipProgressEntity("p2", "g1", "2026-10-15", value = 1, clientUpdatedAt = 3))
        assertEquals(1, dao.progressCount("g1"))
        assertEquals(4, dao.progress("g1", "2026-10-15")!!.value)

        dao.deleteGoalPermanently("g1")
        assertEquals(0, dao.progressCount("g1"))
    }

    @Test
    fun readingPositionIsASingleRow() = runTest {
        val dao = db.readingPositionDao()
        dao.set(ReadingPositionEntity(surah = 2, ayah = 255, clientUpdatedAt = 1))
        dao.set(ReadingPositionEntity(surah = 18, ayah = 1, clientUpdatedAt = 2))
        assertEquals(18, dao.observe().first()!!.surah)
    }

    @Test
    fun outboxIsFirstInFirstOut() = runTest {
        val dao = db.syncOutboxDao()
        dao.enqueue(SyncOutboxEntity(entity = "bookmark", entityId = "b1", op = "upsert", idempotencyKey = "k1"))
        dao.enqueue(SyncOutboxEntity(entity = "bookmark", entityId = "b2", op = "delete", idempotencyKey = "k2"))
        val first = dao.peek(1).single()
        assertEquals("b1", first.entityId)

        dao.recordFailure(first.seq, "network.offline")
        assertEquals(1, dao.peek(1).single().attempts)
        dao.remove(first.seq)
        assertEquals("b2", dao.peek(10).single().entityId)
    }
}
