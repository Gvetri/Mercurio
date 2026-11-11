package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * The application-owned elevation scale. [raised] marks the active conversation
 * pane on larger displays; every other surface stays on the base content plane.
 */
@Immutable
data class HermesElevation(
    val level0: Dp,
    val level1: Dp,
    val level2: Dp,
    val level3: Dp,
    val level4: Dp,
    val level5: Dp,
) {
    val raised: Dp
        get() = level2
}
