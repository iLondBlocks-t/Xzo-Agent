package com.xzo.agent.core

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.xzo.agent.agent.FileBridge
import com.xzo.agent.data.db.MessageEntity
import com.xzo.agent.util.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports a conversation as a real, shareable PDF using Android's own
 * [PdfDocument] — no library, no service, works offline.
 */
object PdfExporter {

    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 42f

    suspend fun export(
        title: String,
        messages: List<MessageEntity>,
        files: FileBridge
    ): FileBridge.SavedFile? = withContext(Dispatchers.Default) {
        val doc = PdfDocument()

        val body = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = 0xFF17171C.toInt()
        }
        val who = Paint(body).apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val meta = Paint(body).apply {
            textSize = 8.5f
            color = 0xFF6E6E7A.toInt()
        }
        val heading = Paint(body).apply {
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN + 12f

        fun newPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun line(text: String, paint: Paint, indent: Float = 0f) {
            val maxWidth = PAGE_W - 2 * MARGIN - indent
            wrap(text, paint, maxWidth).forEach { l ->
                if (y > PAGE_H - MARGIN) newPage()
                canvas.drawText(l, MARGIN + indent, y, paint)
                y += paint.textSize * 1.45f
            }
        }

        canvas.drawText(title.take(60), MARGIN, y, heading)
        y += 26f
        canvas.drawText(
            "Xzo Agent · exported " + SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date()),
            MARGIN, y, meta
        )
        y += 24f

        messages.forEach { m ->
            val label = when (m.role) {
                "user" -> "You"
                "assistant" -> "Xzo" + (m.model?.let { " · ${it.substringAfterLast('/')}" } ?: "")
                else -> m.role
            }
            if (y > PAGE_H - MARGIN - 40) newPage()
            line(label, who)
            Markdown.parse(m.content).forEach { block ->
                when (block) {
                    is Markdown.Block.Heading -> { y += 4f; line(block.text, who) }
                    is Markdown.Block.Paragraph -> line(block.text, body)
                    is Markdown.Block.Bullet -> block.items.forEachIndexed { i, item ->
                        line((if (block.ordered) "${i + 1}. " else "-  ") + item, body, 10f)
                    }
                    is Markdown.Block.Code -> block.code.lines().forEach { line(it, meta, 10f) }
                    is Markdown.Block.Quote -> line(block.text, meta, 10f)
                    is Markdown.Block.Table -> {
                        line(block.header.joinToString("  |  "), who, 6f)
                        block.rows.forEach { line(it.joinToString("  |  "), body, 6f) }
                    }
                    Markdown.Block.Divider -> y += 8f
                }
            }
            y += 12f
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()

        val safe = title.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF _-]"), "").trim().ifBlank { "chat" }
        files.createBinaryDocument("$safe.pdf", "application/pdf", out.toByteArray())
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val out = mutableListOf<String>()
        text.split("\n").forEach { raw ->
            var current = StringBuilder()
            raw.split(" ").forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) out += current.toString()
                    current = StringBuilder(word)
                }
            }
            out += current.toString()
        }
        return out
    }
}
