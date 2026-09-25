package com.xzo.agent.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A strictly gray / black / white palette.
 * Every surface in the app references these tokens – no scattered hex values.
 */
object XzoPalette {
    // Neutral ramp
    val Ink000 = Color(0xFF000000)
    val Ink050 = Color(0xFF07070A)
    val Ink100 = Color(0xFF0B0B0D)
    val Ink150 = Color(0xFF121216)
    val Ink200 = Color(0xFF17171C)
    val Ink300 = Color(0xFF212128)
    val Ink400 = Color(0xFF2C2C34)
    val Ink500 = Color(0xFF3A3A44)
    val Ink600 = Color(0xFF4E4E5A)
    val Gray500 = Color(0xFF6E6E7A)
    val Gray400 = Color(0xFF8E8E9A)
    val Gray300 = Color(0xFFAFAFB8)
    val Gray200 = Color(0xFFCFCFD6)
    val Gray150 = Color(0xFFE0E0E5)
    val Gray100 = Color(0xFFECECF0)
    val Gray050 = Color(0xFFF4F4F6)
    val White = Color(0xFFFFFFFF)
    val OffWhite = Color(0xFFFAFAFB)
}

data class XzoGradient(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val glow: Color
)

val LightGradient = XzoGradient(
    top = XzoPalette.White,
    mid = XzoPalette.Gray100,
    bottom = XzoPalette.Gray200,
    glow = XzoPalette.White
)

val DarkGradient = XzoGradient(
    top = XzoPalette.Ink300,
    mid = XzoPalette.Ink100,
    bottom = XzoPalette.Ink000,
    glow = XzoPalette.Ink400
)
