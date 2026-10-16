package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.hadith.BookState
import com.zikra.core.common.result.AppError
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.hadith.HadithBook
import com.zikra.core.hadith.HadithTerms

/** The six books (WP 3.4c): download each on request; open those on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithBooksScreen(
    books: Map<HadithBook, BookState>,
    onOpen: (HadithBook) -> Unit,
    onDownload: (HadithBook) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.hadith_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
            actions = {
                IconButton(onClick = onSearch, modifier = Modifier.testTag(HADITH_SEARCH_BUTTON_TAG)) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.hadith_search))
                }
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            HadithBook.entries.forEach { book ->
                BookRow(book, books[book] ?: BookState.NotDownloaded, arabic, onOpen, onDownload)
            }
            Text(
                text = stringResource(R.string.hadith_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(ZikraSpacing.screenGutter),
            )
        }
    }
}

@Composable
private fun BookRow(
    book: HadithBook,
    state: BookState,
    arabic: Boolean,
    onOpen: (HadithBook) -> Unit,
    onDownload: (HadithBook) -> Unit,
) {
    val installed = state == BookState.Installed
    ListItem(
        headlineContent = { Text(text = HadithTerms.title(book, arabic)) },
        supportingContent = (state as? BookState.Failed)?.let { failed ->
            {
                Text(text = stringResource(failed.error.messageRes()), color = MaterialTheme.colorScheme.error)
            }
        },
        trailingContent = {
            when (state) {
                BookState.Installed -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                BookState.Downloading -> CircularProgressIndicator(Modifier.size(24.dp).testTag(downloadingTag(book)))
                BookState.NotDownloaded, is BookState.Failed -> FilledTonalButton(
                    onClick = { onDownload(book) },
                    modifier = Modifier.testTag(downloadTag(book)),
                ) {
                    val label = if (state is BookState.Failed) R.string.hadith_retry else R.string.hadith_download
                    Text(text = stringResource(label))
                }
            }
        },
        modifier = (if (installed) Modifier.clickable(role = Role.Button) { onOpen(book) } else Modifier)
            .testTag(bookTag(book)),
    )
}

private fun AppError.messageRes(): Int = when (this) {
    AppError.Offline -> R.string.hadith_failed_offline
    AppError.Storage -> R.string.hadith_failed_storage
    else -> R.string.hadith_failed_integrity
}

internal const val HADITH_SEARCH_BUTTON_TAG = "hadith_search_button"
internal fun bookTag(book: HadithBook) = "hadith_book_${book.key}"
internal fun downloadTag(book: HadithBook) = "hadith_download_${book.key}"
internal fun downloadingTag(book: HadithBook) = "hadith_downloading_${book.key}"
