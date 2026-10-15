package com.zikra.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.designsystem.theme.ZikraTheme

/**
 * Inline banner for a state the user should know about (exact alarms denied, offline, …).
 * Meaning is carried by the [message] text, not by colour alone. Announced politely to TalkBack.
 */
@Composable
fun StatusBanner(
    message: String,
    kind: BannerKind,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val accent: Color = when (kind) {
        BannerKind.Info -> MaterialTheme.colorScheme.primary
        BannerKind.Warning -> ZikraTheme.extendedColors.warning
        BannerKind.Error -> MaterialTheme.colorScheme.error
        BannerKind.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent, MaterialTheme.shapes.medium)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZikraSpacing.lg, vertical = ZikraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZikraSpacing.sm),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, color = accent, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
