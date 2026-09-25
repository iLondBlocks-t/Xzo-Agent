package com.xzo.agent.core

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.xzo.agent.util.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Microphone capture (m4a/AAC) for Groq Whisper transcription.
 * Needs only RECORD_AUDIO, requested at the moment the user taps the mic.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean = runCatching {
        stop()
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val f = File(dir, "note_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioSamplingRate(16_000)
        r.setAudioEncodingBitRate(32_000)
        r.setOutputFile(f.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        output = f
        true
    }.getOrElse { stop(); false }

    /** Stops recording and returns the recorded file (or null). */
    fun stop(): File? {
        val r = recorder ?: return output.also { output = null }
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        val f = output
        output = null
        return f?.takeIf { it.exists() && it.length() > 1024 }
    }

    fun cancel() {
        stop()?.delete()
    }

    fun cleanup() {
        runCatching { File(context.cacheDir, "voice").listFiles()?.forEach { it.delete() } }
    }
}

/** Offline text-to-speech for reading answers aloud. Uses the device engine — no network, no cost. */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    @Volatile
    var speaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking = true }
            override fun onDone(utteranceId: String?) { speaking = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking = false }
        })
    }

    suspend fun speak(markdown: String, locale: Locale = Locale.getDefault()) = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext
        if (!ready) return@withContext
        runCatching { engine.language = locale }
        val plain = Markdown.stripMarkdown(markdown).take(3800)
        if (plain.isBlank()) return@withContext
        engine.speak(plain, TextToSpeech.QUEUE_FLUSH, null, "xzo-${System.nanoTime()}")
        speaking = true
    }

    fun stop() {
        runCatching { tts?.stop() }
        speaking = false
    }

    fun release() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}
