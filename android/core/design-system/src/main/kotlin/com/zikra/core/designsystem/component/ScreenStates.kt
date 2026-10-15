package com.zikra.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.zikra.core.designsystem.theme.ZikraSpacing

/** Empty state: what is missing and, optionally, how to fill it (DESIGN_SYSTEM_AR.md UX rules). */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = MessageState(title, message, actionLabel, onAction, modifier)

/** Error state with retry. The text comes from the stable error code (AppError.code), never an exception. */
@Composable
fun ErrorState(
    title: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) = MessageState(title, message, retryLabel, onRetry, modifier)

@Composable
private fun MessageState(
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ZikraSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZikraSpacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            ZikraPrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = ZikraSpacing.sm),
            )
        }
    }
}

/**
 * Loading placeholder shaped like the content it replaces (no centered spinner, DESIGN wireframe 06).
 * [loadingDescription] is announced once by screen readers.
 */
@Composable
fun SkeletonBlock(
    height: Dp,
    loadingDescription: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val alpha = if (animate) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = SKELETON_MIN_ALPHA,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MS), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
        value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.large)
            .semantics { contentDescription = loadingDescription },
    )
}

private const val SKELETON_MIN_ALPHA = 0.5f
private const val SKELETON_PULSE_MS = 900
