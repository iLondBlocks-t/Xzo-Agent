package com.xzo.agent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/** Soft pulsing dots shown while the agent is planning / calling a tool. */
@Composable
fun ThinkingDots(
    modifier: Modifier = Modifier,
    dotSize: Int = 7,
    label: String? = null
) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(3) { index ->
            val a by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(680, delayMillis = index * 170, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(dotSize.dp)
                    .alpha(a)
                    .scale(0.75f + a * 0.35f)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), CircleShape)
            )
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** Verified chip (check icon + label) that fades and scales in once self-verification passes. */
@Composable
fun VerifiedChip(
    visible: Boolean,
    verdict: String? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.85f),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    RoundedCornerShape(50)
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val label = com.xzo.agent.ui.LocalStrings.current.verified
            Text(
                text = if (verdict.isNullOrBlank() || verdict == "none") label
                else "$label · $verdict".take(48),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Thin shimmering line used as a subtle streaming indicator. */
@Composable
fun StreamingCaret(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(
        0.15f, 1f,
        infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "caretAlpha"
    )
    Box(
        modifier
            .size(width = 8.dp, height = 16.dp)
            .alpha(a)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
    )
}
