package com.xzo.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.xzo.agent.ui.theme.LocalXzoGradient

/**
 * Smooth diagonal gradient that slowly drifts, cycling
 * gray → near-black → off-white depending on the active theme.
 */
@Composable
fun GradientBackground(
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val g = LocalXzoGradient.current
    val transition = rememberInfiniteTransition(label = "bg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val shift = (phase - 0.5f) * 0.35f

                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to g.top,
                            (0.45f + shift).coerceIn(0.15f, 0.85f) to g.mid,
                            1f to g.bottom
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    size = Size(w, h)
                )

                // Soft radial glow that breathes with the phase – adds depth without colour.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(g.glow.copy(alpha = 0.22f), g.glow.copy(alpha = 0f)),
                        center = Offset(w * (0.22f + phase * 0.55f), h * (0.12f + phase * 0.18f)),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * (0.22f + phase * 0.55f), h * (0.12f + phase * 0.18f))
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(g.bottom.copy(alpha = 0.30f), g.bottom.copy(alpha = 0f)),
                        center = Offset(w * (0.85f - phase * 0.4f), h * (0.88f - phase * 0.2f)),
                        radius = w * 0.75f
                    ),
                    radius = w * 0.75f,
                    center = Offset(w * (0.85f - phase * 0.4f), h * (0.88f - phase * 0.2f))
                )
            },
        content = content
    )
}
