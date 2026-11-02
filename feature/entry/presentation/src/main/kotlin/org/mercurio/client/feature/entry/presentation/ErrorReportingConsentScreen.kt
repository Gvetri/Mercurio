package org.mercurio.client.feature.entry.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The first screen of a fresh install: one question about crash, error, and not-responding reports,
 * then the answer that lets the gateway setup open. It carries no status strip, no icon, and no
 * answered state, because it shows once and the next screen owns the connection.
 */
@Composable
internal fun ErrorReportingConsentContent(
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalHermesDesignTokens.current.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        ConsentWordmarkBar()
        ConsentDecision(onEvent = onEvent, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ConsentWordmarkBar() {
    val tokens = LocalHermesDesignTokens.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.large)
                .background(tokens.colorScheme.surface)
                .heightIn(min = CONSENT_BAR_HEIGHT)
                .padding(start = tokens.spacing.m, end = tokens.spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.shell_wordmark),
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 0.06.em,
            color = tokens.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsentDecision(
    onEvent: (EntryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalHermesDesignTokens.current
    val spacing = tokens.spacing
    BoxWithConstraints(
        modifier = modifier.clip(tokens.shapes.large).background(tokens.colorScheme.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.consent_question),
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(spacing.l))
            Text(
                text = stringResource(R.string.consent_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.l))
            Text(
                text = stringResource(R.string.consent_consequence),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(spacing.l))
            Button(
                onClick = { onEvent(EntryUiEvent.ErrorReportingConsentGranted) },
                modifier = Modifier.fillMaxWidth().height(CONSENT_ACTION_HEIGHT),
                shape = tokens.shapes.small,
            ) {
                Text(text = stringResource(R.string.consent_send_reports))
            }
            Spacer(modifier = Modifier.height(spacing.s))
            OutlinedButton(
                onClick = { onEvent(EntryUiEvent.ErrorReportingConsentDeclined) },
                modifier = Modifier.fillMaxWidth().height(CONSENT_ACTION_HEIGHT),
                shape = tokens.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.colorScheme.onSurface),
            ) {
                Text(text = stringResource(R.string.consent_do_not_send))
            }
        }
    }
}

private val CONSENT_BAR_HEIGHT = 48.dp

private val CONSENT_ACTION_HEIGHT = 48.dp
