package com.zikra.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zikra.core.designsystem.theme.ZikraSizes

/**
 * Primary action. [text] must come from stringResource. While [loading], the button is disabled
 * and announces [loadingDescription] to screen readers; its width does not jump.
 */
@Composable
fun ZikraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .heightIn(min = ZikraSizes.minTouchTarget)
            .semantics { if (loading && loadingDescription != null) stateDescription = loadingDescription },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        ButtonContent(text, loading)
    }
}

/** Secondary action (outlined). */
@Composable
fun ZikraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ZikraSizes.minTouchTarget),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ButtonContent(text: String, loading: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        // Keep the label laid out (invisible) so the button keeps its size while loading.
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.alpha(if (loading) 0f else 1f),
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(ZikraSizes.iconSm),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        }
    }
}
