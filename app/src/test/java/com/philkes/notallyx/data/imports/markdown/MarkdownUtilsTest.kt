package com.philkes.notallyx.data.imports.markdown

import com.philkes.notallyx.data.model.*
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownUtilsTest {

    private fun note(body: String, spans: List<SpanRepresentation>): BaseNote {
        val now = System.currentTimeMillis()
        return BaseNote(
            id = 0L,
            type = Type.NOTE,
            folder = Folder.NOTES,
            color = BaseNote.COLOR_DEFAULT,
            title = "",
            pinned = false,
            timestamp = now,
            modifiedTimestamp = now,
            labels = emptyList(),
            body = BodyString(body),
            spans = spans,
            items = emptyList(),
            images = emptyList(),
            files = emptyList(),
            audios = emptyList(),
            reminders = emptyList(),
            viewMode = NoteViewMode.EDIT,
        )
    }

    @Test
    fun export_complex_note_to_markdown() {
        val body = "Start bolditalic code link strike\nSecond line"
        val boldStart = body.indexOf("bolditalic")
        val boldEnd = boldStart + "bolditalic".length
        val codeStart = body.indexOf("code")
        val codeEnd = codeStart + 4
        val linkStart = body.indexOf("link")
        val linkEnd = linkStart + 4
        val strikeStart = body.indexOf("strike")
        val strikeEnd = strikeStart + 6

        val spans =
            listOf(
                // nested bold+italic on same range
                SpanRepresentation(boldStart, boldEnd, bold = true),
                SpanRepresentation(boldStart, boldEnd, italic = true),
                // code
                SpanRepresentation(codeStart, codeEnd, monospace = true),
                // link
                SpanRepresentation(
                    linkStart,
                    linkEnd,
                    link = true,
                    linkData = "https://example.com",
                ),
                // strike
                SpanRepresentation(strikeStart, strikeEnd, strikethrough = true),
            )

        val md = note(body, spans).toMarkdown()
        val expected =
            "Start **_bolditalic_** `code` [link](https://example.com) ~~strike~~\nSecond line"
        assertEquals(expected, md)
    }

    @Test
    fun parse_markdown_resource_with_image_and_all_spans() {
        val url =
            javaClass.classLoader!!.getResource("markdown/all_spans_and_image.md")
                ?: throw IllegalStateException("Test resource not found")
        val input = url.readText(StandardCharsets.UTF_8)

        val (body, spans) = parseBodyAndSpansFromMarkdown(input)

        val expectedBody = buildString {
            append("# Title\n")
            append("This has bold, italic, code, strike, and a link.\n")
            append(
                "Also an image inline: ![Alt text](https://example.com/image.png) and nested bolditalic.\n"
            )
            append("Next line.")
        }
        assertEquals(expectedBody, body)

        // Helper to get text slice for a span
        fun SpanRepresentation.slice(): String = body.substring(start, end)

        // Verify presence of each supported span type with correct text content
        assertTrue(spans.any { it.bold && it.slice() == "bold" })
        assertTrue(spans.any { it.italic && it.slice() == "italic" })
        assertTrue(spans.any { it.monospace && it.slice() == "code" })
        assertTrue(spans.any { it.strikethrough && it.slice() == "strike" })
        assertTrue(spans.any { it.link && it.slice() == "link" && it.linkData == "https://ex.com" })

        // Nested bold+italic on "bolditalic" should yield overlapping spans for the same range
        val boldItalicStart = body.indexOf("bolditalic")
        val boldItalicEnd = boldItalicStart + "bolditalic".length
        assertTrue(spans.any { it.bold && it.start == boldItalicStart && it.end == boldItalicEnd })
        assertTrue(
            spans.any { it.italic && it.start == boldItalicStart && it.end == boldItalicEnd }
        )

        // Ensure image was ignored except for its alt text; no spans should cover "Alt text"
        val altStart = body.indexOf("Alt text")
        val altEnd = altStart + "Alt text".length
        assertTrue(spans.none { it.start <= altStart && it.end >= altEnd })
    }
}
