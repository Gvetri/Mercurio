package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The component tokens the Material role set cannot express: the island inset
 * fill and the selected session row tint with its two derived foregrounds.
 */
@Immutable
data class HermesComponentColors(
    val inset: Color,
    val sessionSelected: Color,
    val onSessionSelected: Color,
    val onSessionSelectedDim: Color,
)
