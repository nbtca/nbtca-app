package space.nbtca.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import space.nbtca.app.core.util.htmlUnescape
import space.nbtca.app.ui.theme.AppColors
import space.nbtca.app.ui.theme.AppThemeColors
import space.nbtca.app.ui.theme.LocalIsDark

/**
 * Markdown 渲染：支持 1~6 级标题、段落、有序/无序列表、引用、代码块、分隔线、
 * 行内链图片（含被链接包裹的图片），以及行内粗体/斜体/代码/可点击链接。
 *
 * @param skipLeadingTitle 正文首行 H1 若与文章标题相同则跳过，避免标题重复。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    skipLeadingTitle: String? = null
) {
    val blocks = remember(markdown, skipLeadingTitle) {
        skipDuplicateTitle(parseMarkdownBlocks(markdown), skipLeadingTitle)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block -> RenderBlock(block) }
    }
}

private fun skipDuplicateTitle(blocks: List<MdBlock>, title: String?): List<MdBlock> {
    if (title.isNullOrBlank()) return blocks
    val index = blocks.indexOfFirst { it !is MdBlock.Rule }
    if (index < 0) return blocks
    val first = blocks[index]
    return if (first is MdBlock.Heading && first.level == 1 &&
        first.text.trim().equals(title.trim(), ignoreCase = true)
    ) {
        blocks.filterIndexed { i, _ -> i != index }
    } else {
        blocks
    }
}

@Composable
private fun RenderBlock(block: MdBlock) {
    val isDark = LocalIsDark.current
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.text, isDark),
            fontSize = when (block.level) {
                1 -> 22.sp
                2 -> 19.sp
                else -> 17.sp
            },
            fontWeight = FontWeight.Bold,
            lineHeight = when (block.level) {
                1 -> 30.sp
                2 -> 27.sp
                else -> 24.sp
            },
            color = AppThemeColors.textPrimary
        )

        is MdBlock.Paragraph -> Text(
            text = inline(block.text, isDark),
            fontSize = 16.sp,
            lineHeight = 26.sp,
            color = AppThemeColors.textPrimary
        )

        is MdBlock.Bullet -> Row {
            Text("•", color = AppColors.Primary, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            Text(
                text = inline(block.text, isDark),
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = AppThemeColors.textPrimary
            )
        }

        is MdBlock.Ordered -> Row {
            Text(
                text = "${block.number}.",
                color = AppColors.Primary,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = inline(block.text, isDark),
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = AppThemeColors.textPrimary
            )
        }

        is MdBlock.Quote -> Row {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(20.dp)
                    .background(AppColors.Primary, RoundedCornerShape(2.dp))
            )
            Text(
                text = inline(block.text, isDark),
                fontSize = 15.sp,
                lineHeight = 24.sp,
                color = AppThemeColors.textSecondary
            )
        }

        is MdBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) Color(0xFF1E1E20) else Color(0xFFF4F4F6),
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = block.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = AppThemeColors.textPrimary
            )
        }

        is MdBlock.Image -> MarkdownImage(src = block.src, alt = block.alt)

        MdBlock.Rule -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AppThemeColors.divider)
        )
    }
}

@Composable
private fun MarkdownImage(src: String, alt: String) {
    SubcomposeAsyncImage(
        model = src,
        contentDescription = alt,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.FillWidth,
        loading = {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(AppThemeColors.surfaceElevated))
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(AppThemeColors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = alt,
                    tint = AppThemeColors.textSecondary
                )
            }
        },
        success = { SubcomposeAsyncImageContent() }
    )
}

/** 行内语法：**粗体**、*斜体*、`代码`、[链接](url)；图片在内联文本中直接略过（已按块渲染）。 */
@Composable
private fun inline(text: String, isDark: Boolean): AnnotatedString {
    val codeBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF0F0F2)
    return remember(text, isDark) { buildInline(text, codeBg) }
}

private fun buildInline(raw: String, codeBg: Color): AnnotatedString {
    val text = htmlUnescape(raw)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**"); i += 2
                    }
                }

                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("__"); i += 2
                    }
                }

                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('*'); i += 1
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBg,
                                color = AppColors.Primary,
                                fontSize = 15.sp
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append('`'); i += 1
                    }
                }

                // 图片语法（可能还包在链接里）：整段跳过，避免出现多余的 "!" 与重复 alt。
                text.startsWith("![", i) -> {
                    val closeBracket = text.indexOf(']', i + 2)
                    val hasUrl = closeBracket > 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '('
                    if (hasUrl) {
                        val end = text.indexOf(')', closeBracket + 2)
                        if (end > 0) {
                            i = advancePastLinkWrapper(text, end + 1)
                        } else {
                            i = text.length
                        }
                    } else {
                        i += 2
                    }
                }

                text[i] == '[' -> {
                    val close = text.indexOf(']', i + 1)
                    val hasUrl = close > i + 1 && close + 1 < text.length && text[close + 1] == '('
                    if (hasUrl) {
                        val end = text.indexOf(')', close + 2)
                        if (end > close + 2) {
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, end).trim()
                            withLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = AppColors.Primary,
                                            textDecoration = TextDecoration.Underline
                                        )
                                    )
                                )
                            ) {
                                append(label.ifEmpty { url })
                            }
                            i = end + 1
                        } else {
                            append(text[i]); i += 1
                        }
                    } else {
                        append(text[i]); i += 1
                    }
                }

                else -> {
                    append(text[i]); i += 1
                }
            }
        }
    }
}

/** 若图片外还包了一层 [![...](..)](..)，跳到外层右括号之后。 */
private fun advancePastLinkWrapper(text: String, afterImage: Int): Int {
    var index = afterImage
    if (index < text.length && text[index] == ']' &&
        index + 1 < text.length && text[index + 1] == '('
    ) {
        val end = text.indexOf(')', index + 2)
        if (end > 0) index = end + 1
    }
    return index
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Ordered(val number: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Image(val alt: String, val src: String) : MdBlock
    data object Rule : MdBlock
}

private val FENCE: String = String(charArrayOf('`', '`', '`'))
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val ORDERED = Regex("^(\\d+)[.)]\\s+(.*)$")
private val IMAGE = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)[^)]*\\)")
private val LINK_WRAPPED_IMAGE = Regex("\\[(!\\[[^\\]]*\\]\\([^)]*\\))\\]\\([^)]*\\)")

private fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.setLength(0)
        if (text.isEmpty()) return
        text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { chunk -> addTextWithImages(blocks, chunk) }
    }

    // 先把 [![alt](src)](href) 收敛成 ![alt](src)，简化后续解析。
    val normalized = markdown.replace("\r\n", "\n").replace(LINK_WRAPPED_IMAGE, "$1")

    normalized.split("\n").forEach { raw ->
        val line = raw.trimEnd()
        if (line.trimStart().startsWith(FENCE)) {
            if (inCode) {
                blocks.add(MdBlock.Code(codeBuffer.toString().trimEnd()))
                codeBuffer.setLength(0)
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            return@forEach
        }
        if (inCode) {
            codeBuffer.appendLine(line)
            return@forEach
        }

        val trimmed = line.trim()
        val heading = HEADING.find(trimmed)
        when {
            trimmed.isEmpty() -> flushParagraph()
            heading != null -> {
                flushParagraph()
                blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
            }
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph(); blocks.add(MdBlock.Rule)
            }
            trimmed.startsWith("> ") -> {
                flushParagraph(); blocks.add(MdBlock.Quote(trimmed.removePrefix("> ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushParagraph()
                addTextWithImages(blocks, trimmed.substring(2), bullet = true)
            }
            ORDERED.matches(trimmed) -> {
                val result = ORDERED.find(trimmed)!!
                flushParagraph()
                addTextWithImages(blocks, result.groupValues[2], orderNumber = result.groupValues[1])
            }
            else -> paragraph.append(trimmed).append('\n')
        }
    }
    if (inCode && codeBuffer.isNotEmpty()) blocks.add(MdBlock.Code(codeBuffer.toString().trimEnd()))
    flushParagraph()
    return blocks
}

/**
 * 一行里可能同时有文字与图片（如 "![分享过程](url)分享过程" 或
 * "这是协会的 logo！ ![logo](url)"），按图片切分成 段落/图片/段落。
 */
private fun addTextWithImages(
    blocks: MutableList<MdBlock>,
    line: String,
    bullet: Boolean = false,
    orderNumber: String? = null
) {
    val matches = IMAGE.findAll(line).toList()
    if (matches.isEmpty()) {
        when {
            orderNumber != null -> blocks.add(MdBlock.Ordered(orderNumber, line))
            bullet -> blocks.add(MdBlock.Bullet(line))
            else -> blocks.add(MdBlock.Paragraph(line))
        }
        return
    }

    var cursor = 0
    matches.forEach { match ->
        val before = line.substring(cursor, match.range.first).trim()
        if (before.isNotEmpty()) {
            when {
                orderNumber != null -> blocks.add(MdBlock.Ordered(orderNumber, before))
                bullet -> blocks.add(MdBlock.Bullet(before))
                else -> blocks.add(MdBlock.Paragraph(before))
            }
        }
        blocks.add(MdBlock.Image(alt = match.groupValues[1], src = match.groupValues[2]))
        cursor = match.range.last + 1
    }
    val after = line.substring(cursor).trim()
    // 图片后紧跟的说明文字通常就是图片标题，直接丢弃避免与 alt 重复。
    if (after.isNotEmpty() && after != matches.last().groupValues[1].trim()) {
        blocks.add(MdBlock.Paragraph(after))
    }
}
