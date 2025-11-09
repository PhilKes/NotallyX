package com.philkes.notallyx.data.imports.markdown

import com.philkes.notallyx.data.model.SpanRepresentation
import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.markdown.MarkdownRenderer

/**
 * Parses a Markdown string into a plain text body and a list of SpanRepresentation Only supported
 * spans are mapped: bold, italic, monospace (code), strikethrough, and link. Any other formatting
 * is ignored and raw text is preserved.
 */
fun parseBodyAndSpansFromMarkdown(input: String): Pair<String, List<SpanRepresentation>> {
    val extensions: List<Extension> = listOf(StrikethroughExtension.create())
    // Prepare renderer with GFM strikethrough
    val renderer = MarkdownRenderer.builder().extensions(extensions).build()
    val parser: Parser = Parser.builder().extensions(extensions).build()
    val document: Node = parser.parse(input)

    val sb = StringBuilder()
    val spans = mutableListOf<SpanRepresentation>()

    // Visitor that builds text and collects spans based on the node ranges in the built text
    document.accept(
        object : AbstractVisitor() {
            override fun visit(text: Text) {
                sb.append(text.literal)
            }

            override fun visit(softLineBreak: SoftLineBreak) {
                sb.append('\n')
            }

            override fun visit(hardLineBreak: HardLineBreak) {
                sb.append('\n')
            }

            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                if (sb.isNotEmpty() && (sb.last() != '\n')) sb.append('\n')
            }

            override fun visit(code: Code) {
                val start = sb.length
                sb.append(code.literal)
                val end = sb.length
                spans.add(SpanRepresentation(start, end, monospace = true))
            }

            override fun visit(emphasis: Emphasis) {
                val start = sb.length
                visitChildren(emphasis)
                val end = sb.length
                if (start != end) spans.add(SpanRepresentation(start, end, italic = true))
            }

            override fun visit(strongEmphasis: StrongEmphasis) {
                val start = sb.length
                visitChildren(strongEmphasis)
                val end = sb.length
                if (start != end) spans.add(SpanRepresentation(start, end, bold = true))
            }

            override fun visit(customNode: CustomNode) {
                if (customNode is Strikethrough) {
                    val start = sb.length
                    visitChildren(customNode)
                    val end = sb.length
                    if (start != end)
                        spans.add(SpanRepresentation(start, end, strikethrough = true))
                } else {
                    sb.append(renderer.render(customNode))
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                }
            }

            override fun visit(link: Link) {
                val start = sb.length
                visitChildren(link)
                val end = sb.length
                if (start != end)
                    spans.add(
                        SpanRepresentation(start, end, link = true, linkData = link.destination)
                    )
            }

            // For other blocks (Headings, Lists, etc.), just add raw markdown
            override fun visit(heading: Heading) {
                sb.append(renderer.render(heading))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(blockQuote: BlockQuote) {
                sb.append(renderer.render(blockQuote))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(bulletList: BulletList) {
                sb.append(renderer.render(bulletList))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(orderedList: OrderedList) {
                sb.append(renderer.render(orderedList))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(listItem: ListItem) {
                sb.append(renderer.render(listItem))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(thematicBreak: ThematicBreak) {
                sb.append(renderer.render(thematicBreak))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }

            override fun visit(image: Image) {
                sb.append(renderer.render(image))
                if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }
        }
    )

    // Trim a final single trailing newline introduced by paragraph/list handling
    val body = if (sb.endsWith("\n")) sb.trimEnd('\n').toString() else sb.toString()
    return Pair(body, spans)
}

/**
 * Build a Markdown string from a plain text body and span representations using CommonMark AST and
 * MarkdownRenderer. Only supported inline styles are emitted: bold, italic, code, strikethrough and
 * links.
 */
fun createMarkdownFromBodyAndSpans(body: String, spans: List<SpanRepresentation>): String {
    // Prepare renderer with GFM strikethrough
    val extensions: List<Extension> = listOf(StrikethroughExtension.create())
    val renderer = MarkdownRenderer.builder().extensions(extensions).build()

    val document = Document()

    // Split into paragraphs by newline. Within a paragraph, emit SoftLineBreak on single newlines.
    // We'll create one Paragraph and insert SoftLineBreak for every '\n'. This preserves newlines
    // in output.
    val paragraph = Paragraph()
    document.appendChild(paragraph)

    // Collect all boundary indices where styles change
    val boundaries = java.util.TreeSet<Int>()
    boundaries.add(0)
    boundaries.add(body.length)
    for (s in spans) {
        val start = s.start.coerceIn(0, body.length)
        val end = s.end.coerceIn(0, body.length)
        if (start < end) {
            boundaries.add(start)
            boundaries.add(end)
        }
    }

    fun activeFor(rangeStart: Int, rangeEnd: Int): List<SpanRepresentation> {
        if (rangeStart >= rangeEnd) return emptyList()
        return spans.filter { it.start < rangeEnd && it.end > rangeStart }
    }

    val it = boundaries.iterator()
    if (!it.hasNext()) return ""
    var prev = it.next()
    while (it.hasNext()) {
        val next = it.next()
        if (prev >= next) {
            prev = next
            continue
        }
        val segment = body.substring(prev, next)
        // Handle embedded newlines by splitting and inserting SoftLineBreak nodes
        val parts = segment.split('\n')
        for ((idx, part) in parts.withIndex()) {
            if (part.isNotEmpty()) {
                appendStyledInline(paragraph, part, activeFor(prev, next))
            }
            if (idx < parts.lastIndex) {
                paragraph.appendChild(SoftLineBreak())
            }
        }
        prev = next
    }

    return renderer.render(document).trimEnd('\n')
}

private fun appendStyledInline(parent: Node, text: String, actives: List<SpanRepresentation>) {
    // Determine flags in priority order. Code cannot contain children; Link will wrap others.
    val hasLink = actives.any { it.link }
    val linkData = actives.lastOrNull { it.link }?.linkData
    val bold = actives.any { it.bold }
    val italic = actives.any { it.italic }
    val strike = actives.any { it.strikethrough }
    val code = actives.any { it.monospace }

    fun appendTextNode(container: Node) {
        container.appendChild(Text(text))
    }

    if (code) {
        // Inline code has no children
        parent.appendChild(Code(text))
        return
    }

    var container: Node = parent
    if (hasLink && !linkData.isNullOrEmpty()) {
        val link = Link(linkData, null)
        container.appendChild(link)
        container = link
    }
    if (bold) {
        val n = StrongEmphasis()
        container.appendChild(n)
        container = n
    }
    if (italic) {
        val n = Emphasis()
        container.appendChild(n)
        container = n
    }
    if (strike) {
        val n: CustomNode = Strikethrough("~~")
        container.appendChild(n)
        container = n
    }

    appendTextNode(container)
}
