package com.zikra.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import java.io.File

/** Quran reader display choices (WP 2.10). */
@Serializable
data class ReaderPreferences(
    val version: Int = 1,
    /** Multiplier of the base Quran text size, [MIN_SCALE]..[MAX_SCALE]. */
    val textScale: Float = 1f,
    /** true = night colours in the reader; null = follow the phone's dark theme. */
    val night: Boolean? = null,
    /** Show the translation under each ayah; null = on when the UI language has a bundled translation. */
    val showTranslation: Boolean? = null,
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.5f
        const val STEP = 0.1f
    }
}

/** reader.json: display preferences only; may be backed up. */
class ReaderPreferencesStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : JsonFileStore<ReaderPreferences>(produceFile, ReaderPreferences.serializer(), ReaderPreferences(), scope) {
    companion object {
        const val FILE_NAME = "reader.json"
    }
}
