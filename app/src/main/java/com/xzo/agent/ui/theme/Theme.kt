package com.xzo.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import com.xzo.agent.core.ThemeMode

val LocalXzoGradient = staticCompositionLocalOf { LightGradient }

private val DarkColors = darkColorScheme(
    primary = XzoPalette.Gray150,
    onPrimary = XzoPalette.Ink100,
    primaryContainer = XzoPalette.Ink400,
    onPrimaryContainer = XzoPalette.Gray100,
    secondary = XzoPalette.Gray400,
    onSecondary = XzoPalette.Ink100,
    secondaryContainer = XzoPalette.Ink300,
    onSecondaryContainer = XzoPalette.Gray150,
    tertiary = XzoPalette.Gray300,
    onTertiary = XzoPalette.Ink100,
    background = XzoPalette.Ink100,
    onBackground = XzoPalette.Gray100,
    surface = XzoPalette.Ink150,
    onSurface = XzoPalette.Gray100,
    surfaceVariant = XzoPalette.Ink300,
    onSurfaceVariant = XzoPalette.Gray300,
    outline = XzoPalette.Ink500,
    outlineVariant = XzoPalette.Ink400,
    error = Color(0xFFD9D9DE),
    onError = XzoPalette.Ink100,
    scrim = Color(0x99000000)
)

private val LightColors = lightColorScheme(
    primary = XzoPalette.Ink200,
    onPrimary = XzoPalette.White,
    primaryContainer = XzoPalette.Gray150,
    onPrimaryContainer = XzoPalette.Ink200,
    secondary = XzoPalette.Gray500,
    onSecondary = XzoPalette.White,
    secondaryContainer = XzoPalette.Gray100,
    onSecondaryContainer = XzoPalette.Ink300,
    tertiary = XzoPalette.Gray400,
    onTertiary = XzoPalette.White,
    background = XzoPalette.Gray050,
    onBackground = XzoPalette.Ink150,
    surface = XzoPalette.White,
    onSurface = XzoPalette.Ink150,
    surfaceVariant = XzoPalette.Gray100,
    onSurfaceVariant = XzoPalette.Ink500,
    outline = XzoPalette.Gray200,
    outlineVariant = XzoPalette.Gray150,
    error = Color(0xFF3A3A44),
    onError = XzoPalette.White,
    scrim = Color(0x66000000)
)

private val XzoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val XzoTypography = Typography(
    displaySmall = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = XzoFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private fun Typography.scaledBy(factor: Float): Typography {
    if (factor == 1f) return this
    fun TextStyle.s() = copy(
        fontSize = fontSize * factor,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight
    )
    return copy(
        displaySmall = displaySmall.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s()
    )
}

@Composable
fun XzoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val gradient = if (dark) DarkGradient else LightGradient

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalXzoGradient provides gradient) {
        val scaled = remember(fontScale) { XzoTypography.scaledBy(fontScale) }
        MaterialTheme(
            colorScheme = colors,
            typography = scaled,
            shapes = XzoShapes,
            content = content
        )
    }
}
