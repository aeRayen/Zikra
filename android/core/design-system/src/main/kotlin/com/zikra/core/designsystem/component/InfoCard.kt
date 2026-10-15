package com.zikra.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zikra.core.designsystem.theme.ZikraSpacing

/**
 * Card with a small accent [label] (e.g. «الصلاة القادمة») above its [content].
 * Clickable when [onClick] is set; the label is a heading for screen-reader navigation.
 */
@Composable
fun InfoCard(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(ZikraSpacing.lg)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) { body() }
    }
}
