package com.zikra.app.quran

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.R
import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeUiState
import com.zikra.app.ui.HomeScreen
import com.zikra.app.ui.QuranBookmarksScreen
import com.zikra.app.ui.homeCardTag
import com.zikra.app.ui.savedTag
import com.zikra.core.database.user.UserDatabase
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.quran.QuranText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranUserDataTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
    private var clock = 1_000L
    private val data = QuranUserData(db.bookmarkDao(), db.readingPositionDao(), now = { clock })
    private val quran = QuranText.open(context)

    @After
    fun tearDown() {
        db.close()
        quran.close()
    }

    @Test
    fun toggleAddsRemovesWithTombstoneAndReAdds() = runBlocking {
        val kursi = AyahRef(2, 255)
        data.toggle(kursi)
        assertEquals(listOf(kursi), data.bookmarked.first())
        clock = 2_000
        data.toggle(kursi)
        assertTrue(data.bookmarked.first().isEmpty())
        // The removal stays as a tombstone for sync, marked dirty.
        val tombstone = requireNotNull(db.bookmarkDao().find(QuranUserData.ITEM_AYAH, "2:255"))
        assertTrue(tombstone.deleted && tombstone.dirty && tombstone.clientUpdatedAt == 2_000L)
        clock = 3_000
        data.toggle(kursi) // same row comes back (unique per ayah)
        assertEquals(listOf(kursi), data.bookmarked.first())
        assertEquals(tombstone.id, db.bookmarkDao().find(QuranUserData.ITEM_AYAH, "2:255")?.id)
    }

    @Test
    fun newestBookmarkFirst() = runBlocking {
        data.toggle(AyahRef(1, 1))
        clock = 5_000
        data.toggle(AyahRef(36, 1))
        assertEquals(listOf(AyahRef(36, 1), AyahRef(1, 1)), data.bookmarked.first())
    }

    @Test
    fun lastReadIsRememberedAndResolved() = runBlocking {
        assertNull(data.lastRead.first())
        data.saveLastRead(AyahRef(18, 10))
        val saved = requireNotNull(continueReading(data, { quran }, kotlinx.coroutines.Dispatchers.Unconfined).first())
        assertEquals(18, saved.surah.number)
        assertEquals(10, saved.ayah.ayah)
        assertEquals(quran.ayahs(18)[9].text, saved.ayah.text)
    }

    @Test
    fun malformedKeysAreIgnored() {
        assertNull(AyahRef.parse("2-255"))
        assertNull(AyahRef.parse("x:1"))
        assertEquals(AyahRef(2, 255), AyahRef.parse("2:255"))
    }

    private fun saved(surah: Int, ayah: Int) = SavedAyah(
        requireNotNull(quran.surah(surah)),
        quran.ayahs(surah)[ayah - 1]
    )

    @Test
    fun bookmarksScreenListsOpensAndRemoves() {
        val opened = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<SavedAyah>()
        val item = saved(2, 255)
        rule.setContent {
            ZikraTheme {
                QuranBookmarksScreen(
                    listOf(item),
                    onOpen = { s, a -> opened += s to a },
                    onRemove = { removed += it },
                    onBack = {}
                )
            }
        }
        rule.onNodeWithTag(savedTag(2, 255)).performClick()
        rule.onNodeWithContentDescription(
            rule.activity.getString(R.string.reader_bookmark_remove, "255")
        ).performClick()
        assertEquals(listOf(2 to 255), opened)
        assertEquals(listOf(item), removed)
    }

    @Test
    fun emptyBookmarksExplainHowToSave() {
        val noOpen: (Int, Int) -> Unit = { _, _ -> }
        rule.setContent {
            ZikraTheme { QuranBookmarksScreen(emptyList(), onOpen = noOpen, onRemove = {}, onBack = {}) }
        }
        rule.onNodeWithText(rule.activity.getString(R.string.quran_bookmarks_empty)).assertExists()
    }

    @Test
    fun homeContinueReadingCardOpensTheAyah() {
        val opened = mutableListOf<Pair<Int, Int>>()
        val card = HomeCard.ContinueReading(saved(18, 10))
        rule.setContent {
            ZikraTheme { HomeScreen(HomeUiState(true, listOf(card)), onContinueReading = { s, a -> opened += s to a }) }
        }
        rule.onNodeWithTag(homeCardTag(card)).performClick()
        assertEquals(listOf(18 to 10), opened)
    }
}
