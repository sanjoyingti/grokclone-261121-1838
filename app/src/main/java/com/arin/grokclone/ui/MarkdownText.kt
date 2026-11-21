package com.arin.grokclone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

private val NUMBERED_PATTERN = Regex("^(\\d{1,3})[.)]\\s+(.*)$")

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").split("\n")
    val paragraph = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCode = false
    var codeLanguage = ""

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
            paragraph.clear()
        }
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

        if (inCode) {
            if (trimmed.startsWith("```")) {
                blocks.add(MdBlock.Code(codeLanguage, codeBuffer.toString().trimEnd('\n')))
                codeBuffer.clear()
                codeLanguage = ""
                inCode = false
            } else {
                codeBuffer.append(line).append('\n')
            }
            continue
        }

        if (trimmed.startsWith("```")) {
            flushParagraph()
            inCode = true
            codeLanguage = trimmed.removePrefix("```").trim()
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushParagraph()
                blocks.add(MdBlock.Bullet(trimmed.substring(2).trim()))
            }
            NUMBERED_PATTERN.matches(trimmed) -> {
                flushParagraph()
                val match = NUMBERED_PATTERN.find(trimmed)
                if (match != null) {
                    val number = match.groupValues[1].toIntOrNull() ?: 1
                    blocks.add(MdBlock.Numbered(number, match.groupValues[2].trim()))
                }
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        blocks.add(MdBlock.Code(codeLanguage, codeBuffer.toString().trimEnd('\n')))
    }
    flushParagraph()
    return blocks
}

fun buildInlineMarkdown(
    text: String,
    linkColor: Color,
    codeColor: Color,
    codeBackground: Color
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        val length = text.length
        while (index < length) {
            val ch = text[index]
            when {
                ch == '`' -> {
                    val end = text.indexOf('`', index + 1)
                    if (end > index) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = codeColor,
                                background = codeBackground
                            )
                        ) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(ch)
                        index++
                    }
                }
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(ch)
                        index++
                    }
                }
                ch == '*' || ch == '_' -> {
                    val end = text.indexOf(ch, index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(ch)
                        index++
                    }
                }
                ch == '[' -> {
                    val closeBracket = text.indexOf(']', index + 1)
                    val openParen = if (closeBracket > 0) text.indexOf('(', closeBracket) else -1
                    val closeParen = if (openParen > 0) text.indexOf(')', openParen) else -1
                    if (closeBracket > 0 && openParen == closeBracket + 1 && closeParen > openParen) {
                        val label = text.substring(index + 1, closeBracket)
                        val url = text.substring(openParen + 1, closeParen)
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(label)
                        }
                        index = closeParen + 1
                    } else {
                        append(ch)
                        index++
                    }
                }
                else -> {
                    append(ch)
                    index++
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = MaterialTheme.typography.bodyLarge

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(code = block.code, language = block.language)
                is MdBlock.Heading -> Text(
                    text = buildInlineMarkdown(block.text, linkColor, codeColor, codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                is MdBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Text(text = "•", color = color, style = bodyStyle)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildInlineMarkdown(block.text, linkColor, codeColor, codeBackground),
                        style = bodyStyle,
                        color = color
                    )
                }
                is MdBlock.Numbered -> Row(verticalAlignment = Alignment.Top) {
                    Text(text = "${block.number}.", color = color, style = bodyStyle)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildInlineMarkdown(block.text, linkColor, codeColor, codeBackground),
                        style = bodyStyle,
                        color = color
                    )
                }
                is MdBlock.Paragraph -> Text(
                    text = buildInlineMarkdown(block.text, linkColor, codeColor, codeBackground),
                    style = bodyStyle,
                    color = color
                )
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { copyToClipboard(context, "code", code) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText(label, value))
}
