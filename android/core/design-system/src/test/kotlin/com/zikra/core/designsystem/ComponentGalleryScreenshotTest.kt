package com.zikra.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.zikra.core.designsystem.component.BannerKind
import com.zikra.core.designsystem.component.ErrorState
import com.zikra.core.designsystem.component.InfoCard
import com.zikra.core.designsystem.component.SkeletonBlock
import com.zikra.core.designsystem.component.StatusBanner
import com.zikra.core.designsystem.component.ZikraPrimaryButton
import com.zikra.core.designsystem.component.ZikraSecondaryButton
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.designsystem.theme.ZikraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots: {ar, en} x {light, dark} x {100%, 200% font} (BUILD_PLAN WP 1.5, TEST_STRATEGY §1).
 * Record: ./gradlew :core:design-system:recordRoborazziDebug — Verify (CI): verifyRoborazziDebug.
 *
 * The animation clock is paused: the gallery contains an indeterminate spinner (loading button),
 * and waiting for an infinite animation to go idle made each capture take ~8 minutes.
 * Sample strings are test fixtures, not product copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xhdpi")
class ComponentGalleryScreenshotTest {

    private companion object {
        const val SPINNER_FRAME_MS = 500L
    }

    @get:Rule
    val rule = createComposeRule()

    private data class Copy(
        val cardLabel: String,
        val cardTitle: String,
        val primary: String,
        val secondary: String,
        val banner: String,
        val bannerAction: String,
        val errorTitle: String,
        val errorMessage: String,
        val retry: String,
        val loading: String,
    )

    private val arabic = Copy(
        cardLabel = "الصلاة القادمة",
        cardTitle = "العصر · ١٥:٢٠",
        primary = "متابعة",
        secondary = "ليس الآن",
        banner = "التنبيه الدقيق غير مسموح؛ قد يتأخر الأذان.",
        bannerAction = "السماح",
        errorTitle = "تعذّر تحميل المحتوى",
        errorMessage = "تحقق من الاتصال ثم أعد المحاولة.",
        retry = "إعادة المحاولة",
        loading = "جارٍ التحميل",
    )

    private val english = Copy(
        cardLabel = "Next prayer",
        cardTitle = "Asr · 3:20 PM",
        primary = "Continue",
        secondary = "Not now",
        banner = "Exact alarms are not allowed; the adhan may be late.",
        bannerAction = "Allow",
        errorTitle = "Couldn't load content",
        errorMessage = "Check your connection and try again.",
        retry = "Retry",
        loading = "Loading",
    )

    /** Language and font scale come from the method's @Config; [name] is the golden file name. */
    private fun shot(name: String, arabicUi: Boolean, dark: Boolean) {
        rule.mainClock.autoAdvance = false
        val direction = if (arabicUi) LayoutDirection.Rtl else LayoutDirection.Ltr
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                ZikraTheme(darkTheme = dark) { Gallery(if (arabicUi) arabic else english) }
            }
        }
        // Fixed point in the spinner animation: visible arc, identical on every run.
        rule.mainClock.advanceTimeBy(SPINNER_FRAME_MS)
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun Gallery(c: Copy) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(ZikraSpacing.screenGutter),
                verticalArrangement = Arrangement.spacedBy(ZikraSpacing.lg),
            ) {
                InfoCard(label = c.cardLabel, onClick = {}) {
                    Text(text = c.cardTitle, style = MaterialTheme.typography.headlineSmall)
                }
                StatusBanner(message = c.banner, kind = BannerKind.Warning, actionLabel = c.bannerAction, onAction = {})
                StatusBanner(message = c.errorMessage, kind = BannerKind.Offline)
                ZikraPrimaryButton(text = c.primary, onClick = {})
                ZikraPrimaryButton(text = c.primary, onClick = {}, loading = true, loadingDescription = c.loading)
                ZikraSecondaryButton(text = c.secondary, onClick = {})
                SkeletonBlock(height = 64.dp, loadingDescription = c.loading, animate = false)
                ErrorState(title = c.errorTitle, message = c.errorMessage, retryLabel = c.retry, onRetry = {})
            }
        }
    }

    @Test
    @Config(qualifiers = "+ar")
    fun arLight100() = shot("gallery_ar_light_font100", arabicUi = true, dark = false)

    @Test
    @Config(qualifiers = "+ar")
    fun arDark100() = shot("gallery_ar_dark_font100", arabicUi = true, dark = true)

    @Test
    @Config(qualifiers = "+ar", fontScale = 2f)
    fun arLight200() = shot("gallery_ar_light_font200", arabicUi = true, dark = false)

    @Test
    @Config(qualifiers = "+ar", fontScale = 2f)
    fun arDark200() = shot("gallery_ar_dark_font200", arabicUi = true, dark = true)

    @Test
    @Config(qualifiers = "+en")
    fun enLight100() = shot("gallery_en_light_font100", arabicUi = false, dark = false)

    @Test
    @Config(qualifiers = "+en")
    fun enDark100() = shot("gallery_en_dark_font100", arabicUi = false, dark = true)

    @Test
    @Config(qualifiers = "+en", fontScale = 2f)
    fun enLight200() = shot("gallery_en_light_font200", arabicUi = false, dark = false)

    @Test
    @Config(qualifiers = "+en", fontScale = 2f)
    fun enDark200() = shot("gallery_en_dark_font200", arabicUi = false, dark = true)
}
