package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.hadith.Hadith
import com.zikra.core.hadith.HadithGrade
import com.zikra.core.hadith.HadithTerms
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem

/** One hadith: its number, the Arabic text verbatim, and every grading with the scholar who gave it. */
@Composable
internal fun HadithCard(hadith: Hadith, showBook: Boolean = false) {
    val language = LocalConfiguration.current.locales[0].language
    val arabic = language == "ar"
    val numerals = NumeralSystem.forLanguage(language)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZikraSpacing.screenGutter)
            .semantics(mergeDescendants = true) {}
            .testTag(hadithTag(hadith)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val number = stringResource(R.string.hadith_number, numerals.apply(hadith.number))
            val label = if (showBook) "${HadithTerms.title(hadith.book, arabic)} · $number" else number
            Text(
                text = Bidi.isolate(label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = hadith.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = TEXT_SP.sp, lineHeight = LINE_SP.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            hadith.grades.filterNot(HadithTerms::isEmpty).forEach { GradeLine(it, arabic) }
        }
    }
}

/** «al-Albani: sahih». Known terms in Arabic for the Arabic UI; anything else exactly as published. */
@Composable
private fun GradeLine(grade: HadithGrade, arabic: Boolean) {
    val scholar = (if (arabic) HadithTerms.scholarArabic(grade.scholar) else null) ?: grade.scholar
    val verdict = (if (arabic) HadithTerms.gradeArabic(grade.grade) else null) ?: grade.grade
    Text(
        text = stringResource(R.string.hadith_grade_line, Bidi.isolate(scholar), Bidi.isolate(verdict)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val TEXT_SP = 18
private const val LINE_SP = 32

internal fun hadithTag(hadith: Hadith) = "hadith_${hadith.book.key}_${hadith.number}"
