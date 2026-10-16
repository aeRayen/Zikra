package com.zikra.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.mutableStateOf
import com.zikra.app.adhan.PrayerAlertNotifier
import com.zikra.app.adhkar.AdhkarReminderNotifier
import com.zikra.core.designsystem.theme.ZikraTheme

/**
 * Single activity. Extends AppCompatActivity because per-app language on Android 12 and lower
 * needs it (AppLocales).
 */
class MainActivity : AppCompatActivity() {
    /** An adhkar chapter to open, from a tapped reminder (WP 2.14c); cleared once shown. */
    private val pendingChapter = mutableStateOf<Int?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Channel names follow the app language (the activity is recreated when it changes).
        PrayerAlertNotifier.ensureChannels(this)
        AdhkarReminderNotifier.ensureChannel(this)
        if (savedInstanceState == null) readChapter(intent)
        enableEdgeToEdge()
        setContent {
            ZikraTheme {
                ZikraApp(
                    widthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                    openAdhkarChapter = pendingChapter.value,
                    onAdhkarChapterOpened = { pendingChapter.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readChapter(intent)
    }

    private fun readChapter(intent: Intent?) {
        val chapter = intent?.getIntExtra(AdhkarReminderNotifier.EXTRA_CHAPTER, NO_CHAPTER) ?: NO_CHAPTER
        if (chapter != NO_CHAPTER) pendingChapter.value = chapter
    }

    private companion object {
        const val NO_CHAPTER = -1
    }
}
