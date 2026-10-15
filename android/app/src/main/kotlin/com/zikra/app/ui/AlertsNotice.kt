package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.core.designsystem.theme.ZikraSpacing

/** Why prayer alerts may not reach the user, shown above the times with one button to fix it. */
enum class AlertsNotice { NONE, NEED_NOTIFICATION_PERMISSION, INEXACT_DELIVERY }

@Composable
internal fun AlertsNoticeCard(notice: AlertsNotice, onFix: () -> Unit) {
    val (title, message, button) = when (notice) {
        AlertsNotice.NONE -> return
        AlertsNotice.NEED_NOTIFICATION_PERMISSION -> Triple(
            R.string.alerts_permission_title,
            R.string.alerts_permission_message,
            R.string.alerts_permission_button,
        )
        AlertsNotice.INEXACT_DELIVERY -> Triple(
            R.string.alerts_inexact_title,
            R.string.alerts_inexact_message,
            R.string.alerts_inexact_button,
        )
    }
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(ALERTS_NOTICE_TAG)) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(text = stringResource(message), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onFix) { Text(text = stringResource(button)) }
        }
    }
}

internal const val ALERTS_NOTICE_TAG = "prayer_alerts_notice"
