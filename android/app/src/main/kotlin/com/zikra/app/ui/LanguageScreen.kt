package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.zikra.app.R
import com.zikra.core.i18n.Language
import com.zikra.core.i18n.SupportedLanguages

/**
 * App language picker. Each language is shown by its own native name (I18N_SPEC §1).
 * [selected] null = follow the device language. Selecting applies immediately, without restart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
    selected: Language?,
    onSelect: (Language?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.language_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        Column(Modifier.selectableGroup()) {
            LanguageOption(
                name = stringResource(R.string.language_follow_device),
                isSelected = selected == null,
                onClick = { onSelect(null) },
            )
            SupportedLanguages.all.forEach { language ->
                LanguageOption(
                    name = language.nativeName,
                    isSelected = selected == language,
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(name: String, isSelected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = name) },
        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
        modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
    )
}
