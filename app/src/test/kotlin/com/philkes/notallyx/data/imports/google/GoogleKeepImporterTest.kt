package com.philkes.notallyx.data.imports.google

import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleKeepImporterTest {

    private val importer = GoogleKeepImporter()

    @Test
    fun `parseToBaseNote text note with labels`() {
        val json =
            """
            {
              "color": "DEFAULT",
              "isTrashed": false,
              "isPinned": false,
              "isArchived": false,
              "textContent": "This is some note, nothing special",
              "textContentHtml": "<p dir=\"ltr\" style=\"line-height:1.38;margin-top:0.0pt;margin-bottom:0.0pt;\"><span style=\"font-size:16.0pt;font-family:'Google Sans';color:#000000;background-color:transparent;font-weight:400;font-style:normal;font-variant:normal;text-decoration:none;vertical-align:baseline;white-space:pre;white-space:pre-wrap;\">This is some note, nothing special<\/span><\/p>",
              "title": "Normal Note",
              "userEditedTimestampUsec": 1729518341059120,
              "createdTimestampUsec": 1729518341059000,
              "labels": [
                {
                  "name": "Label1"
                },
                {
                  "name": "Label2"
                }
              ]
            }
        """
                .trimIndent()
        val expected =
            createBaseNote(
                title = "Normal Note",
                timestamp = 1729518341059,
                modifiedTimestamp = 1729518341059,
                labels = listOf("Label1", "Label2"),
                body = "This is some note, nothing special",
            )
        val actual = with(importer) { json.parseToBaseNote() }

        assertEquals(expected, actual)
    }

    @Test
    fun `parseToBaseNote trashed note`() {
        val json =
            """
            {
              "isTrashed": true,
              "isArchived": false,
              "title": "Trashed Note",
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual)
            .extracting("title", "folder")
            .containsExactly("Trashed Note", Folder.DELETED)
    }

    @Test
    fun `parseToBaseNote archived note`() {
        val json =
            """
            {
              "isTrashed": false,
              "isArchived": true,
              "title": "Archived Note",
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual)
            .extracting("title", "folder")
            .containsExactly("Archived Note", Folder.ARCHIVED)
    }

    @Test
    fun `parseToBaseNote pinned note`() {
        val json =
            """
            {
              "isPinned": true,
              "title": "Pinned Note",
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual).extracting("title", "pinned").containsExactly("Pinned Note", true)
    }

    @Test
    fun `parseToBaseNote list note`() {
        val json =
            """
            {
              "title": "List Note",
               "listContent": [
                    {
                      "text": "Task1",
                      "isChecked": false
                    },
                    {
                      "text": "Task2",
                      "isChecked": true
                    }
                ]
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual)
            .extracting("type", "title", "items")
            .containsExactly(
                Type.LIST,
                "List Note",
                listOf(
                    ListItem("Task1", false, false, 0, mutableListOf()),
                    ListItem("Task2", true, false, 1, mutableListOf()),
                ),
            )
    }

    @Test
    fun `parseToBaseNote note with images`() {
        val json =
            """
            {
              "title": "Image Note",
              "attachments": [
                {
                  "filePath": "image.jpg",
                  "mimetype": "image/jpeg"
                }
              ],
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual)
            .extracting("title", "images")
            .containsExactly(
                "Image Note",
                listOf(FileAttachment("image.jpg", "image.jpg", "image/jpeg")),
            )
    }

    @Test
    fun `parseToBaseNote note with files`() {
        val json =
            """
            {
              "title": "File Note",
              "attachments": [
                {
                  "filePath": "document.doc",
                  "mimetype": "application/msword"
                }
              ],
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual)
            .extracting("title", "files")
            .containsExactly(
                "File Note",
                listOf(FileAttachment("document.doc", "document.doc", "application/msword")),
            )
    }

    @Test
    fun `parseToBaseNote note with audio`() {
        val json =
            """
            {
              "title": "Audio Note",
              "attachments": [
                {
                  "filePath": "audio.3gp",
                  "mimetype": "audio/3gp"
                }
              ],
            }
        """
                .trimIndent()

        val actual = with(importer) { json.parseToBaseNote() }

        assertThat(actual.title).isEqualTo("Audio Note")
        assertThat(actual.audios[0].name).isEqualTo("audio.3gp")
    }

    companion object {
        fun createBaseNote(
            id: Long = 0L,
            type: Type = Type.NOTE,
            folder: Folder = Folder.NOTES,
            color: Color = Color.DEFAULT,
            title: String = "Note",
            pinned: Boolean = false,
            timestamp: Long = System.currentTimeMillis(),
            modifiedTimestamp: Long = System.currentTimeMillis(),
            labels: List<String> = listOf(),
            body: String = "",
            spans: List<SpanRepresentation> = listOf(),
            items: List<ListItem> = listOf(),
            images: List<FileAttachment> = listOf(),
            files: List<FileAttachment> = listOf(),
            audios: List<Audio> = listOf(),
            reminders: List<Reminder> = listOf(),
        ): BaseNote {
            return BaseNote(
                id,
                type,
                folder,
                color,
                title,
                pinned,
                timestamp,
                modifiedTimestamp,
                labels,
                body,
                spans,
                items,
                images,
                files,
                audios,
                reminders,
            )
        }
    }
}
