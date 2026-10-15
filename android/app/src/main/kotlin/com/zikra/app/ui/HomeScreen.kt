package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.zikra.app.R
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing

/**
 * «Today». Cards are contributed by features as they are implemented (ADR-017); until then the
 * screen says so honestly instead of showing sample data.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(ZikraSpacing.screenGutter)) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        EmptyState(
            title = stringResource(R.string.home_empty_title),
            message = stringResource(R.string.home_empty_message),
        )
    }
}
