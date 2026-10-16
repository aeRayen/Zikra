package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.memorization.MemorizationUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.memorization.Memorization

/** Memorization (WP 3.9): today's new pages and reviews, the plan, and what was memorized before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationScreen(
    state: MemorizationUiState,
    actions: MemorizationActions,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.memorization_title)) },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        when {
            state.unavailable -> EmptyState(
                title = stringResource(R.string.memorization_title),
                message = stringResource(R.string.quran_unavailable),
            )
            state.loaded -> LazyColumn(Modifier.fillMaxSize().testTag(MEMORIZATION_LIST_TAG)) {
                if (saveFailed) {
                    item {
                        Text(
                            text = stringResource(R.string.error_save_failed),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(ZikraSpacing.screenGutter),
                        )
                    }
                }
                progress(state)
                if (state.plan != null) today(state, actions)
                item { PlanCard(state.plan, actions.onSetPlan) }
                before(state, actions)
            }
        }
    }
}

private fun LazyListScope.progress(state: MemorizationUiState) {
    item {
        val numerals = rememberNumerals()
        Column(Modifier.padding(ZikraSpacing.screenGutter).testTag(MEMORIZATION_PROGRESS_TAG)) {
            Text(
                text = stringResource(
                    R.string.memorization_progress,
                    numerals.apply(state.memorized.toString()),
                    numerals.apply(Memorization.PAGE_COUNT.toString()),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            LinearProgressIndicator(
                progress = { state.memorized.toFloat() / Memorization.PAGE_COUNT },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private fun LazyListScope.today(state: MemorizationUiState, actions: MemorizationActions) {
    item { SectionHeading(stringResource(R.string.memorization_new_today)) }
    if (state.newPages.isEmpty()) {
        item {
            val done = state.memorized == Memorization.PAGE_COUNT
            Hint(stringResource(if (done) R.string.memorization_finished else R.string.memorization_no_new))
        }
    }
    items(state.newPages, key = { "new-${it.page}" }) { NewPageRow(it, actions.onOpenPage, actions.onMemorized) }
    item { SectionHeading(stringResource(R.string.memorization_review_today)) }
    if (state.reviews.isEmpty()) {
        item { Hint(stringResource(R.string.memorization_no_reviews)) }
    } else {
        item { Hint(stringResource(R.string.memorization_review_hint)) }
    }
    items(state.reviews, key = { "review-${it.page}" }) { ReviewRow(it, actions.onOpenPage, actions.onReviewed) }
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
}

private fun LazyListScope.before(state: MemorizationUiState, actions: MemorizationActions) {
    item { SectionHeading(stringResource(R.string.memorization_before)) }
    item { Hint(stringResource(R.string.memorization_before_hint)) }
    items(state.juz, key = { "juz-${it.juz}" }) { JuzRow(it, actions.onMemorizedJuz) }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ZikraSpacing.screenGutter, vertical = 4.dp),
    )
}

internal const val MEMORIZATION_LIST_TAG = "memorization_list"
internal const val MEMORIZATION_PROGRESS_TAG = "memorization_progress"
