package com.zikra.app.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.zikra.core.i18n.Language
import com.zikra.core.i18n.SupportedLanguages

/**
 * In-app language (per-app locale). Uses the platform LocaleManager on Android 13+ and AppCompat's
 * stored locale on older versions (autoStoreLocales in the manifest). Changes apply to all
 * activities without restarting the app. The language picker UI arrives with onboarding/settings.
 */
object AppLocales {
    /** null = follow the device language. */
    fun set(language: Language?) {
        val locales = if (language == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /** The app-specific language, or null when following the device. */
    fun current(): Language? {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return if (tags.isEmpty()) null else SupportedLanguages.resolve(tags.substringBefore(','))
    }
}
