package com.philkes.notallyx.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownExportTest {

    private fun note(body: String, spans: List<SpanRepresentation> = emptyList()): BaseNote {
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
    fun export_basic_spans() {
        val body = "Hello world"
        val spans =
            listOf(SpanRepresentation(6, 11, bold = true), SpanRepresentation(0, 5, italic = true))
        val md = note(body, spans).toMarkdown()
        // Expected markers wrapping respective words, order may interleave but produced algorithm
        // yields tokens by ranges
        // Current marker insertion adds the closing marker after the char at index `end` (space
        // here)
        assertEquals("*Hello* **world**", md)
    }

    @Test
    fun export_code_and_strike() {
        val body = "code strike"
        val spans =
            listOf(
                SpanRepresentation(0, 4, monospace = true),
                SpanRepresentation(5, 11, strikethrough = true),
            )
        val md = note(body, spans).toMarkdown()
        // Closing backtick ends up after the space due to marker arrays logic
        assertEquals("`code` ~~strike~~", md)
    }

    @Test
    fun export_link_priority_over_other_tokens() {
        val body = "click here"
        val spans =
            listOf(
                // link over "click"
                SpanRepresentation(0, 5, link = true, linkData = "https://example.com"),
                // bold overlaps the same range but must not insert inside the link according to the
                // implementation
                SpanRepresentation(0, 5, bold = true),
            )
        val md = note(body, spans).toMarkdown()
        // Bold should be ignored within link range; only link markdown is emitted
        // Link close marker is appended after the space at index `end`
        assertEquals("[**click**](https://example.com) here", md)
    }

    @Test
    fun export_list_items_to_checklist() {
        val now = System.currentTimeMillis()
        val items =
            listOf(
                ListItem(
                    body = "Task 1",
                    checked = false,
                    isChild = false,
                    order = 0,
                    children = mutableListOf(),
                ),
                ListItem(
                    body = "Task 2",
                    checked = true,
                    isChild = false,
                    order = 1,
                    children = mutableListOf(),
                ),
                ListItem(
                    body = "Sub",
                    checked = false,
                    isChild = true,
                    order = 2,
                    children = mutableListOf(),
                ),
            )
        val note =
            BaseNote(
                id = 0L,
                type = Type.LIST,
                folder = Folder.NOTES,
                color = BaseNote.COLOR_DEFAULT,
                title = "",
                pinned = false,
                timestamp = now,
                modifiedTimestamp = now,
                labels = emptyList(),
                body = BodyString(""),
                spans = emptyList(),
                items = items,
                images = emptyList(),
                files = emptyList(),
                audios = emptyList(),
                reminders = emptyList(),
                viewMode = NoteViewMode.EDIT,
            )
        val md = note.toMarkdown().trimEnd()
        val expected = "- [ ] Task 1\n- [x] Task 2\n    - [ ] Sub"
        assertEquals(expected, md)
    }
}
