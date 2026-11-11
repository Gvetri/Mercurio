package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * The application-owned spacing scale: the eight Pack D steps. Surfaces read these
 * steps instead of literal values.
 */
@Immutable
data class HermesSpacing(
    val xxs: Dp,
    val xs: Dp,
    val s: Dp,
    val m: Dp,
    val l: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp,
)
