package com.zikra.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.os.UserManagerCompat
import com.zikra.app.adhan.PrayerAlertNotifier
import com.zikra.app.adhan.PrayerAlerts
import com.zikra.app.auth.KeystoreTokenStore
import com.zikra.app.home.HomeCardProvider
import com.zikra.app.home.PrayerHomeCards
import com.zikra.app.location.AndroidDeviceLocator
import com.zikra.app.location.DeviceLocator
import com.zikra.app.prayer.PrayerTimesSource
import com.zikra.app.quran.QuranContent
import com.zikra.app.quran.QuranHomeCards
import com.zikra.app.quran.QuranUserData
import com.zikra.core.database.ZikraDatabases
import com.zikra.core.database.user.UserDatabase
import com.zikra.core.network.AuthRepository
import com.zikra.core.network.GatedTransport
import com.zikra.core.network.NetworkGate
import com.zikra.core.network.OkHttpTransport
import com.zikra.core.network.SessionHolder
import com.zikra.core.network.SupabaseClient
import com.zikra.core.network.SupabaseConfig
import com.zikra.core.places.PlaceDirectory
import com.zikra.core.places.PlaceLookup
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.QuranText
import com.zikra.core.quran.QuranTranslation
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.core.settings.ReaderPreferencesStore
import com.zikra.platform.alarm.AlarmKind
import com.zikra.platform.alarm.AlarmRegistrations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency wiring for the app process (a DI framework can replace it later, ADR-003). */
class AppContainer(context: Context) {
    val sessions = SessionHolder()
    private val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    /** False without Supabase values or while e-mail sign-in is disabled (ADR-029): guest-only, no sign-in button. */
    val accountsAvailable: Boolean = config.isConfigured && BuildConfig.EMAIL_SIGN_IN_ENABLED

    val supabase = SupabaseClient(config, GatedTransport(NetworkGate(sessions), OkHttpTransport()))
    val auth = AuthRepository(supabase, KeystoreTokenStore(context.applicationContext), sessions)

    // Opened on first use (copies the bundled database once): always called from an IO dispatcher.
    val places: PlaceLookup by lazy { PlaceDirectory.open(context) }

    // Own file, excluded from cloud backup (backup_rules.xml): the location stays on this device (ADR-018).
    val locationStore = LocationStore({ File(context.filesDir, LOCATION_FILE) })
    val deviceLocator: DeviceLocator = AndroidDeviceLocator(context)

    // user.db (bookmarks, reading position, goals): on the device; synced only with consent (ADR-024).
    val userDatabase: UserDatabase by lazy { ZikraDatabases.user(context) }
    val quranUserData: QuranUserData by lazy {
        QuranUserData(userDatabase.bookmarkDao(), userDatabase.readingPositionDao())
    }

    // Bundled Quran text, opened on first use (copies the asset once, checks the structure): IO dispatcher only.
    val quran: QuranText by lazy { QuranText.open(context) }

    // Bundled English translation; null if its integrity check fails (the Arabic text is still shown).
    private val englishTranslation: QuranTranslation? by lazy {
        try {
            QuranTranslation.openEnglish(context)
        } catch (_: QuranIntegrityException) {
            null
        }
    }
    val quranContent = QuranContent({ quran }, { englishTranslation })

    val readerPreferences = ReaderPreferencesStore({ File(context.filesDir, READER_FILE) })

    // Prayer choices (method, madhab, adjustments): not location data, so it may be backed up.
    val prayerPreferences = PrayerPreferencesStore({ File(context.filesDir, PRAYER_FILE) })

    /** Lives as long as the process: observes settings to keep prayer alerts scheduled. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prayerAlerts = PrayerAlerts({ places }, locationStore, prayerPreferences)

    /** Home cards of every implemented feature (WP 2.8). Add a provider here when a feature lands. */
    fun homeCardProviders(): List<HomeCardProvider> = listOf(
        PrayerHomeCards(PrayerTimesSource({ places }, locationStore, prayerPreferences, Dispatchers.IO)),
        QuranHomeCards(quranUserData, { quran }, Dispatchers.IO),
    )

    private companion object {
        const val LOCATION_FILE = "datastore/${LocationStore.FILE_NAME}"
        const val PRAYER_FILE = "datastore/${PrayerPreferencesStore.FILE_NAME}"
        const val READER_FILE = "datastore/${ReaderPreferencesStore.FILE_NAME}"
    }
}

class ZikraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Registered even before unlock: an alert may fire right after a reboot (it needs no settings).
        AlarmRegistrations.register(AlarmKind.ADHAN, container.prayerAlerts)
        AlarmRegistrations.register(container.prayerAlerts)
        // After a reboot the process can start BEFORE the first unlock (direct-boot alarm receivers,
        // ADR-033). Credential-protected storage (session, settings) is unreadable then: wait for unlock.
        if (UserManagerCompat.isUserUnlocked(this)) {
            onUserUnlocked()
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unregisterReceiver(this)
                    onUserUnlocked()
                }
            }
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun onUserUnlocked() {
        container.auth.restore()
        PrayerAlertNotifier.ensureChannels(this)
        container.prayerAlerts.observe(this, container.appScope)
    }
}
