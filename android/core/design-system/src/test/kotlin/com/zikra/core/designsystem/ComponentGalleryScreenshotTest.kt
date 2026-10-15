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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots: {ar, en} x {light, dark} x {100%, 200% font} (BUILD_PLAN WP 1.5, TEST_STRATEGY §1).
 * Record: ./gradlew :core:design-system:recordRoborazziDebug — Verify (CI): verifyRoborazziDebug.
 * Sample strings are test fixtures, not product copy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xhdpi")
class ComponentGalleryScreenshotTest {

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

    private fun shot(language: String, dark: Boolean, fontScale: Float) {
        RuntimeEnvironment.setQualifiers("+$language")
        RuntimeEnvironment.setFontScale(fontScale)
        val copy = if (language == "ar") arabic else english
        val direction = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
        val theme = if (dark) "dark" else "light"
        val scale = if (fontScale > 1f) "200" else "100"
        captureRoboImage("src/test/screenshots/gallery_${language}_${theme}_font$scale.png") {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                ZikraTheme(darkTheme = dark) { Gallery(copy) }
            }
        }
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

    @Test fun arLight100() = shot("ar", dark = false, fontScale = 1f)

    @Test fun arDark100() = shot("ar", dark = true, fontScale = 1f)

    @Test fun arLight200() = shot("ar", dark = false, fontScale = 2f)

    @Test fun arDark200() = shot("ar", dark = true, fontScale = 2f)

    @Test fun enLight100() = shot("en", dark = false, fontScale = 1f)

    @Test fun enDark100() = shot("en", dark = true, fontScale = 1f)

    @Test fun enLight200() = shot("en", dark = false, fontScale = 2f)

    @Test fun enDark200() = shot("en", dark = true, fontScale = 2f)
}
