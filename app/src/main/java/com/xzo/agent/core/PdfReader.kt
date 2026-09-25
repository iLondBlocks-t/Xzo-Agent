package com.xzo.agent.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads PDFs with **no third-party library**: Android's own [PdfRenderer] rasterises
 * each page and the bundled ML Kit recogniser turns it back into text.
 *
 * That combination handles scanned PDFs (pure images) as well as digital ones, works
 * fully offline, and costs nothing — where a text-layer-only extractor would return
 * empty strings for anything scanned.
 */
object PdfReader {

    data class Page(val index: Int, val text: String)
    data class Result(val pages: List<Page>, val truncated: Boolean) {
        val text: String
            get() = pages.joinToString("\n\n") { "--- page ${it.index + 1} ---\n${it.text}" }
    }

    const val DEFAULT_MAX_PAGES = 12
    private const val RENDER_WIDTH = 1240 // ≈150 dpi for A4 – enough for OCR, cheap on RAM

    fun isPdf(mime: String?, name: String?): Boolean =
        mime == "application/pdf" || name?.endsWith(".pdf", ignoreCase = true) == true

    suspend fun extract(
        context: Context,
        uri: Uri,
        maxPages: Int = DEFAULT_MAX_PAGES,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): Result? = withContext(Dispatchers.IO) {
        val descriptor: ParcelFileDescriptor = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: return@withContext null

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val pages = mutableListOf<Page>()
        var truncated = false

        try {
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val limit = minOf(total, maxPages)
                truncated = total > limit

                for (i in 0 until limit) {
                    onProgress(i + 1, limit)
                    renderer.openPage(i).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat()
                        val w = RENDER_WIDTH
                        val h = (w * ratio).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE) // PDFs render with transparency otherwise
                        }
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val text = runCatching {
                            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                        }.getOrDefault("")
                        bitmap.recycle()
                        if (text.isNotBlank()) pages += Page(i, text.trim())
                    }
                }
            }
        } catch (t: Throwable) {
            return@withContext null
        } finally {
            runCatching { recognizer.close() }
            runCatching { descriptor.close() }
        }

        Result(pages, truncated)
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        addOnCanceledListener { cont.cancel() }
    }
