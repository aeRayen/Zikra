package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zikra.app.R
import com.zikra.core.designsystem.theme.ZikraSpacing

/** «More» hub: destinations that do not fit in the bottom bar (DESIGN_SYSTEM_AR.md, navigation). */
@Composable
fun MoreScreen(
    onOpenLanguage: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHadith: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(ZikraSpacing.screenGutter)
                .semantics { heading() },
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.hadith_title)) },
            supportingContent = { Text(text = stringResource(R.string.hadith_subtitle)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenHadith).testTag(MORE_HADITH_TAG),
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_language)) },
            // AutoMirrored: points to the end edge, i.e. left in Arabic.
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLanguage),
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_location)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenLocation),
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_account)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(role = Role.Button, onClick = onOpenAccount),
        )
    }
}
internal const val MORE_HADITH_TAG = "more_hadith"
