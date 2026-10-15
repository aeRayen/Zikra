package com.zikra.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.qibla.CompassMath
import com.zikra.app.qibla.QiblaUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import kotlin.math.abs
import kotlin.math.roundToInt

/** Qibla direction (WP 2.6): bearing always shown; live compass when the phone has one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(state: QiblaUiState, onBack: () -> Unit, onChooseLocation: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.qibla_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        when (state) {
            QiblaUiState.Loading -> Unit
            QiblaUiState.NoLocation -> Column(Modifier.padding(ZikraSpacing.screenGutter)) {
                EmptyState(
                    title = stringResource(R.string.prayer_no_location_title),
                    message = stringResource(R.string.prayer_no_location_message),
                )
                TextButton(onClick = onChooseLocation) { Text(text = stringResource(R.string.prayer_choose_location)) }
            }
            is QiblaUiState.Ready -> ReadyQibla(state)
        }
    }
}

@Composable
private fun ReadyQibla(state: QiblaUiState.Ready) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].toLanguageTag())
    fun degrees(value: Double) = Bidi.isolate(numerals.apply(value.roundToInt().toString()))
    val instruction = instruction(state, ::degrees)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZikraSpacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.qibla_bearing, degrees(state.qiblaTrue)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(QIBLA_BEARING_TAG),
        )
        val dialDescription = stringResource(R.string.qibla_dial_description, instruction ?: "")
        CompassDial(
            qiblaTrue = state.qiblaTrue,
            headingTrue = state.headingTrue,
            aligned = state.aligned,
            north = stringResource(R.string.compass_north),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = dialDescription },
        )
        if (instruction != null) {
            Text(
                text = instruction,
                style = MaterialTheme.typography.headlineSmall,
                color = if (state.aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(QIBLA_INSTRUCTION_TAG).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Warnings(state)
    }
}

/** null when there is no live heading (no compass, or first reading not yet in). */
@Composable
private fun instruction(state: QiblaUiState.Ready, degrees: (Double) -> String): String? {
    val heading = state.headingTrue
    // Positive delta = the Qibla is clockwise from where the phone points = turn right.
    val delta = heading?.let { CompassMath.signedDelta(state.qiblaTrue, it) }
    return when {
        delta == null -> null
        state.aligned -> stringResource(R.string.qibla_aligned)
        delta > 0 -> stringResource(R.string.qibla_turn_right, degrees(delta))
        else -> stringResource(R.string.qibla_turn_left, degrees(abs(delta)))
    }
}

@Composable
private fun Warnings(state: QiblaUiState.Ready) {
    val messages = buildList {
        if (!state.hasCompass) add(R.string.qibla_no_compass)
        if (state.hasCompass && state.interference) add(R.string.qibla_interference)
        if (state.hasCompass && state.needsCalibration) add(R.string.qibla_calibrate)
        if (state.hasCompass) add(R.string.qibla_hold_flat)
    }
    messages.forEach { message ->
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = if (message == R.string.qibla_hold_flat) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(qiblaMessageTag(message)),
        )
    }
}

/**
 * Dial drawn in screen coordinates (never mirrored: geography does not flip with the language).
 * With a heading, the dial turns so «N» points to real north; without one, north is up.
 */
@Composable
private fun CompassDial(qiblaTrue: Double, headingTrue: Double?, aligned: Boolean, north: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val northStyle = MaterialTheme.typography.titleMedium.copy(color = colors.primary)
    val arrowColor = if (aligned) colors.primary else colors.tertiary
    Canvas(modifier) {
        val dialRotation = -(headingTrue ?: 0.0).toFloat()
        val radius = size.minDimension / 2 * DIAL_RADIUS
        drawCircle(colors.outline, radius, style = Stroke(width = 2.dp.toPx()))
        rotate(dialRotation) {
            drawTicks(radius, colors.outlineVariant)
            val label = measurer.measure(north, northStyle)
            drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - radius + 8.dp.toPx()))
        }
        rotate(dialRotation + qiblaTrue.toFloat()) { drawArrow(radius, arrowColor) }
    }
}

private fun DrawScope.drawTicks(radius: Float, color: Color) {
    for (degree in 0 until FULL_TURN step TICK_STEP) {
        rotate(degree.toFloat()) {
            val long = degree % MAJOR_TICK == 0
            val length = if (long) radius * MAJOR_TICK_LENGTH else radius * MINOR_TICK_LENGTH
            drawLine(
                color,
                Offset(center.x, center.y - radius),
                Offset(center.x, center.y - radius + length),
                2.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawArrow(radius: Float, color: Color) {
    val tip = Offset(center.x, center.y - radius * ARROW_LENGTH)
    val half = radius * ARROW_HALF_WIDTH
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x - half, center.y)
        lineTo(center.x + half, center.y)
        close()
    }
    drawPath(path, color)
    // The Kaaba end of the arrow: a small square.
    val box = radius * KAABA_SIZE
    drawRect(
        color,
        topLeft = Offset(tip.x - box / 2, tip.y - box * KAABA_OFFSET),
        size = androidx.compose.ui.geometry.Size(box, box)
    )
}

private const val FULL_TURN = 360
private const val TICK_STEP = 15
private const val MAJOR_TICK = 90
private const val DIAL_RADIUS = 0.9f
private const val MAJOR_TICK_LENGTH = 0.12f
private const val MINOR_TICK_LENGTH = 0.06f
private const val ARROW_LENGTH = 0.7f
private const val ARROW_HALF_WIDTH = 0.08f
private const val KAABA_SIZE = 0.12f
private const val KAABA_OFFSET = 1.4f
internal const val QIBLA_BEARING_TAG = "qibla_bearing"
internal const val QIBLA_INSTRUCTION_TAG = "qibla_instruction"
internal fun qiblaMessageTag(message: Int) = "qibla_message_$message"
