package com.xzo.agent.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders an attached image from its inline `data:` URL.
 *
 * Decoding is done off the main thread and downsampled to the requested size, so
 * a 4000-px camera photo never allocates a full-resolution bitmap on a low-end
 * 32-bit phone — which is exactly where this app is meant to run.
 */
@Composable
fun AttachmentThumbnail(
    dataUrl: String,
    size: Dp = 56.dp,
    corner: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetPx = remember(size) { with(density) { size.toPx().toInt().coerceAtLeast(48) } }
    var bitmap by remember(dataUrl, targetPx) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(dataUrl) { mutableStateOf(false) }

    LaunchedEffect(dataUrl, targetPx) {
        val decoded = withContext(Dispatchers.Default) { decode(dataUrl, targetPx) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )

            failed -> Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(size / 3),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> ThinkingDots(dotSize = 4)
        }
    }
}

private fun decode(dataUrl: String, targetPx: Int): Bitmap? = runCatching {
    val base64 = dataUrl.substringAfter("base64,", "")
    if (base64.isEmpty()) return null
    val bytes = Base64.decode(base64, Base64.DEFAULT)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
        sample *= 2
    }
    BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample }
    )
}.getOrNull()
