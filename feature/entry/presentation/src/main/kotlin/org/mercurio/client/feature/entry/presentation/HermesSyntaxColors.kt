package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class HermesSyntaxColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val function: Color,
    val type: Color,
    val comment: Color,
    val punctuation: Color,
    val codeSurface: Color,
)
