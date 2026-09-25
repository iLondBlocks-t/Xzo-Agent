package com.xzo.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzo.agent.util.Markdown

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selectable: Boolean = true
) {
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Markdown.Block.Heading -> Text(
                    text = inline(b.text, color),
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    color = color
                )

                is Markdown.Block.Paragraph -> Text(
                    text = inline(b.text, color),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color
                )

                is Markdown.Block.Code -> CodeBlock(b.language, b.code)

                is Markdown.Block.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEachIndexed { idx, item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = if (b.ordered) "${idx + 1}." else "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = color.copy(alpha = 0.65f),
                                modifier = Modifier.width(if (b.ordered) 26.dp else 18.dp)
                            )
                            Text(
                                text = inline(item, color),
                                style = MaterialTheme.typography.bodyLarge,
                                color = color
                            )
                        }
                    }
                }

                is Markdown.Block.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(color.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inline(b.text, color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color.copy(alpha = 0.85f)
                    )
                }

                is Markdown.Block.Table -> TableBlock(b, color)

                Markdown.Block.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun TableBlock(t: Markdown.Block.Table, color: Color) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .horizontalScroll(scroll)
            .padding(8.dp)
    ) {
        Row {
            t.header.forEach { h ->
                Text(
                    h,
                    modifier = Modifier.width(140.dp).padding(4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = color
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        t.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        inline(cell, color),
                        modifier = Modifier.width(140.dp).padding(4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val scroll = rememberScrollState()
        Text(
            text = code,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Inline markdown: **bold**, *italic*, `code`, ~~strike~~, [text](url). */
fun inline(src: String, base: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val mono = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp)
    while (i < src.length) {
        when {
            src.startsWith("**", i) -> {
                val end = src.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, end)) }
                    i = end + 2
                } else { append(src[i]); i++ }
            }
            src.startsWith("`", i) -> {
                val end = src.indexOf('`', i + 1)
                if (end > 0) {
                    withStyle(mono.copy(color = base.copy(alpha = 0.92f))) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else { append(src[i]); i++ }
            }
            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(src.substring(i + 2, end)) }
                    i = end + 2
                } else { append(src[i]); i++ }
            }
            (src[i] == '*' || src[i] == '_') && i + 1 < src.length && src[i + 1] != ' ' -> {
                val ch = src[i]
                val end = src.indexOf(ch, i + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else { append(src[i]); i++ }
            }
            src[i] == '[' -> {
                val close = src.indexOf(']', i)
                val open = if (close > 0 && close + 1 < src.length && src[close + 1] == '(') close + 1 else -1
                val end = if (open > 0) src.indexOf(')', open) else -1
                if (close > 0 && end > 0) {
                    val label = src.substring(i + 1, close)
                    val url = src.substring(open + 1, end)
                    pushStringAnnotation("URL", url)
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)) {
                        append(label)
                    }
                    pop()
                    i = end + 1
                } else { append(src[i]); i++ }
            }
            else -> { append(src[i]); i++ }
        }
    }
}
