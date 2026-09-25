package com.xzo.agent.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device ML, bundled into the APK — no API key, no network, no rate limit.
 *
 * This is the app's floor of capability: even with both providers down or the
 * phone offline, Xzo can still read text out of a photo and translate it.
 */
object OnDeviceMl {

    /** Offline OCR (Latin script). Returns the recognised text, laid out by line. */
    suspend fun ocr(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return@withContext ""
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(image).await()
            buildString {
                result.textBlocks.forEach { block ->
                    block.lines.forEach { line -> appendLine(line.text) }
                    appendLine()
                }
            }.trim()
        } finally {
            runCatching { recognizer.close() }
            runCatching { bitmap.recycle() }
        }
    }

    /** Offline language identification. Returns a BCP-47 tag or "und". */
    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.IO) {
        val client = LanguageIdentification.getClient()
        try {
            client.identifyLanguage(text.take(2000)).await()
        } catch (t: Throwable) {
            "und"
        } finally {
            runCatching { client.close() }
        }
    }

    data class TranslationResult(val text: String, val from: String, val to: String, val downloaded: Boolean)

    /**
     * Offline translation. The language pair model (~30 MB) is fetched once by ML Kit
     * and then works forever without a network.
     */
    suspend fun translate(
        text: String,
        targetTag: String,
        sourceTag: String? = null,
        requireWifi: Boolean = false
    ): TranslationResult? = withContext(Dispatchers.IO) {
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return@withContext null
        val detected = sourceTag ?: identifyLanguage(text)
        val source = TranslateLanguage.fromLanguageTag(detected) ?: return@withContext null
        if (source == target) return@withContext TranslationResult(text, source, target, false)

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        try {
            val conditions = com.google.mlkit.common.model.DownloadConditions.Builder()
                .apply { if (requireWifi) requireWifi() }
                .build()
            translator.downloadModelIfNeeded(conditions).await()
            val out = translator.translate(text.take(5000)).await()
            TranslationResult(out, source, target, true)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { translator.close() }
        }
    }

    fun supportedLanguageTags(): List<String> = TranslateLanguage.getAllLanguages()
}

/** Bridges a Play-services Task into a coroutine without pulling in kotlinx-play-services. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        addOnCanceledListener { cont.cancel() }
    }
