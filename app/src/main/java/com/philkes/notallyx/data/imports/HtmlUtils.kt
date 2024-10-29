package com.philkes.notallyx.data.imports

import androidx.core.util.PatternsCompat
import com.philkes.notallyx.data.model.SpanRepresentation
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Tries to parse the plain body text from the text as well as parse all text formatting into
 * [SpanRepresentation]s
 */
fun parseBodyAndSpansFromHtml(
    html: String,
    rootTag: String = "body",
    useInnermostRootTag: Boolean = false,
    brTagsAsNewLine: Boolean = true,
    paragraphsAsNewLine: Boolean = false,
): Pair<String, MutableList<SpanRepresentation>> {
    val document: Document = Jsoup.parse(html)
    val rootNoteElement = document.body().getElementsByTag(rootTag)
    if (rootNoteElement.isEmpty()) {
        return Pair("", mutableListOf())
    }
    val rootElement =
        if (useInnermostRootTag) rootNoteElement.last()!! else rootNoteElement.first()!!
    val bodyText = StringBuilder()
    val spans = mutableListOf<SpanRepresentation>()
    processElement(
        rootElement,
        bodyText,
        spans,
        brTagsAsNewLine = brTagsAsNewLine,
        paragraphsAsNewLine = paragraphsAsNewLine,
    )
    return Pair(bodyText.trimEnd().toString(), spans)
}

/**
 * Adds plain text to [bodyText] and adds [SpanRepresentation]s:
 * - `<b>` or `font-weight`>`400` -> [SpanRepresentation.bold]
 * - `<i>` or `font-style:italic` -> [SpanRepresentation.italic]
 * - `<s>` -> [SpanRepresentation.strikethrough]
 * - `<a>` or text starting with `http` -> [SpanRepresentation.link]
 * - `<span>` with `font-family` includes `monospace` or `Source Code Pro` ->
 *   [SpanRepresentation.monospace]
 */
private fun processElement(
    element: Element,
    bodyText: StringBuilder,
    spans: MutableList<SpanRepresentation>,
    brTagsAsNewLine: Boolean,
    paragraphsAsNewLine: Boolean,
) {

    for (child in element.childNodes()) {
        when (child) {
            is TextNode -> {
                // If the child is a text node, append its text and update the offset
                val text = child.text()
                if (text != " ") {
                    bodyText.appendWithUrlCheck(text, spans)
                }
            }

            is Element -> {
                when (child.tagName()) {
                    "b" -> {
                        handleTextSpan(child, bodyText, spans, bold = true)
                    }

                    "i" -> {
                        handleTextSpan(child, bodyText, spans, italic = true)
                    }

                    "s" -> {
                        handleTextSpan(child, bodyText, spans, strikethrough = true)
                    }

                    "span" -> {
                        handleTextSpan(
                            child,
                            bodyText,
                            spans,
                            monospace = child.isMonospaceFont(),
                            bold = child.isBold(),
                            italic = child.isItalic(),
                        )
                    }

                    "a" -> {
                        handleTextSpan(
                            child,
                            bodyText,
                            spans,
                            link = true,
                            linkData = child.attr("href"),
                        )
                    }

                    "div" -> {
                        // div always means new-line, except for first
                        if (bodyText.isNotEmpty()) {
                            bodyText.append("\n")
                        }
                        processElement(
                            child,
                            bodyText,
                            spans,
                            brTagsAsNewLine = brTagsAsNewLine,
                            paragraphsAsNewLine = paragraphsAsNewLine,
                        )
                    }

                    "br" -> {
                        if (brTagsAsNewLine) {
                            bodyText.append("\n")
                        }
                    }

                    else -> {
                        processElement(child, bodyText, spans, brTagsAsNewLine, paragraphsAsNewLine)
                        if (
                            paragraphsAsNewLine &&
                                (child.tagName().startsWith("h") || child.tagName() == "p")
                        ) {
                            bodyText.append("\n")
                        }
                    }
                }
            }
        }
    }
}

private fun handleTextSpan(
    element: Element,
    bodyText: StringBuilder,
    spans: MutableList<SpanRepresentation>,
    bold: Boolean = false,
    italic: Boolean = false,
    link: Boolean = false,
    linkData: String? = null,
    strikethrough: Boolean = false,
    monospace: Boolean = false,
) {
    val text = element.ownText()
    if (bold || italic || link || strikethrough || monospace) {
        val spanStart = bodyText.length
        spans.add(
            SpanRepresentation(
                start = spanStart,
                end = spanStart + text.length,
                bold = bold,
                link = link,
                linkData = linkData,
                italic = italic,
                monospace = monospace,
                strikethrough = strikethrough,
            )
        )
    }

    if (link) {
        bodyText.append(text)
    } else {
        bodyText.appendWithUrlCheck(text, spans)
    }
}

private fun StringBuilder.appendWithUrlCheck(text: String, spans: MutableList<SpanRepresentation>) {
    checkForUrlSpan(text, spans, length)
    append(text)
}

private fun checkForUrlSpan(
    text: String,
    spans: MutableList<SpanRepresentation>,
    elementOffset: Int,
) {
    val matcher = PatternsCompat.WEB_URL.matcher(text)
    if (matcher.find() && matcher.group().startsWith("http")) {
        val url = matcher.group()
        spans.add(
            SpanRepresentation(
                start = elementOffset + matcher.start(),
                end = elementOffset + matcher.end(),
                link = true,
                linkData = url,
            )
        )
    }
}

private fun Element.isMonospaceFont(): Boolean {
    val fontFamily = attr("style")
    return fontFamily.contains("monospace", ignoreCase = true) ||
        fontFamily.contains("Source Code Pro", ignoreCase = true)
}

private fun Element.isBold(): Boolean {
    val style = attr("style")
    return if (style.contains("font-weight")) {
        val fontWeight: String = style.split("font-weight:")[1].split(";")[0].trim()
        return fontWeight.toInt() > 400 // Google Keep normal text has fontWeight 400
    } else false
}

private fun Element.isItalic(): Boolean {
    val style = attr("style")
    return style.contains("font-style:italic")
}
