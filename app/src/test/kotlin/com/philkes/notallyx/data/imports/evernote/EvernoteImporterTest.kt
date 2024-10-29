package com.philkes.notallyx.data.imports.google

import com.philkes.notallyx.data.imports.evernote.EvernoteImporter
import com.philkes.notallyx.data.imports.evernote.EvernoteNote
import com.philkes.notallyx.data.imports.evernote.EvernoteResource
import com.philkes.notallyx.data.imports.evernote.EvernoteResourceAttributes
import com.philkes.notallyx.data.imports.evernote.EvernoteResourceData
import com.philkes.notallyx.data.imports.evernote.EvernoteTag
import com.philkes.notallyx.data.imports.evernote.EvernoteTask
import com.philkes.notallyx.data.imports.evernote.TaskStatus
import com.philkes.notallyx.data.imports.evernote.mapToBaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import java.io.InputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.atIndex
import org.assertj.core.api.Condition
import org.junit.Test

class EvernoteImporterTest {

    private val importer = EvernoteImporter()

    @Test
    fun parseExport() {
        val inputStream: InputStream =
            javaClass.classLoader!!.getResourceAsStream("imports/evernote/Notebook.enex")!!
        val actual = importer.parseExport(inputStream)!!

        assertThat(actual.exportDate).isEqualTo("20241026T102349Z")
        assertThat(actual.notes.sortedBy { it.title })
            .has(
                Condition(
                    {
                        it.title == "Audio" &&
                            it.content.contains("This includes audio") &&
                            it.resources[0].data!!.content.isNotEmpty() &&
                            it.resources[0].mime == "audio/webm"
                    },
                    "Audio",
                ),
                atIndex(0),
            )
            .has(
                Condition(
                    {
                        it.title == "File" &&
                            it.content.contains("This has a file") &&
                            it.resources[0].data!!.content.isNotEmpty() &&
                            it.resources[0].mime == "application/msword" &&
                            it.resources[0].attributes!!.fileName == "document.doc"
                    },
                    "File",
                ),
                atIndex(1),
            )
            .has(
                Condition(
                    {
                        it.title == "Image" &&
                            it.content.contains("This has an image") &&
                            it.resources[0].data!!.content.isNotEmpty() &&
                            it.resources[0].mime == "image/jpeg" &&
                            it.resources[0].width == 1200 &&
                            it.resources[0].height == 1600
                    },
                    "Image",
                ),
                atIndex(2),
            )
            .has(
                Condition(
                    {
                        it.title == "Normal Note" &&
                            it.content.contains("This is some note, nothing special") &&
                            it.tag == listOf(EvernoteTag("Label 1"))
                    },
                    "Normal Note",
                ),
                atIndex(3),
            )
            .has(
                Condition(
                    {
                        it.title == "Pinned List" &&
                            it.tasks.sortedBy { it.sortWeight } ==
                                listOf(
                                    EvernoteTask("Parent1", TaskStatus.OPEN, "B"),
                                    EvernoteTask("Child1", TaskStatus.OPEN, "C"),
                                    EvernoteTask("Child2", TaskStatus.OPEN, "D"),
                                    EvernoteTask("Parent2", TaskStatus.OPEN, "EM"),
                                    EvernoteTask("Parent4", TaskStatus.COMPLETED, "ES"),
                                    EvernoteTask("Parent3", TaskStatus.COMPLETED, "EV"),
                                    EvernoteTask("Child4", TaskStatus.COMPLETED, "F"),
                                )
                    },
                    "Pinned List",
                ),
                atIndex(4),
            )
            .has(
                Condition(
                    {
                        it.title == "Text formatting" &&
                            it.content.containsAll(
                                "This text needs to be fat",
                                "A very italic",
                                "Outdated stuff",
                                "System.out.println(\"Super useful code\");",
                                "https://github.com/PhilKes/NotallyX",
                            )
                    },
                    "Text formatting",
                ),
                atIndex(5),
            )
    }

    @Test
    fun `mapToBaseNote list note`() {
        val actual =
            createEvernoteNote(
                    title = "List Note",
                    tasks =
                        listOf(
                            EvernoteTask("Task1", TaskStatus.OPEN, "A"),
                            EvernoteTask("Task2", TaskStatus.COMPLETED, "B"),
                        ),
                )
                .mapToBaseNote()

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
    fun `mapToBaseNote note with images`() {
        val actual =
            createEvernoteNote(
                    title = "Image Note",
                    resources =
                        listOf(
                            EvernoteResource(
                                data =
                                    EvernoteResourceData(
                                        content =
                                            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBw4QDhAJDRAQGg0YEA8NBw"
                                    ),
                                width = 100,
                                height = 200,
                                mime = "image/jpeg",
                                attributes = EvernoteResourceAttributes("image.jpg"),
                            )
                        ),
                )
                .mapToBaseNote()

        assertThat(actual)
            .extracting("title", "images", "files")
            .containsExactly(
                "Image Note",
                listOf(FileAttachment("image.jpg", "image.jpg", "image/jpeg")),
                listOf<FileAttachment>(),
            )
    }

    @Test
    fun `mapToBaseNote note with files`() {
        val actual =
            createEvernoteNote(
                    title = "File Note",
                    resources =
                        listOf(
                            EvernoteResource(
                                data =
                                    EvernoteResourceData(
                                        content =
                                            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBw4QDhAJDRAQGg0YEA8NBw"
                                    ),
                                width = 100,
                                height = 200,
                                mime = "application/pdf",
                                attributes = EvernoteResourceAttributes("document.pdf"),
                            )
                        ),
                )
                .mapToBaseNote()

        assertThat(actual)
            .extracting("title", "files")
            .containsExactly(
                "File Note",
                listOf(FileAttachment("document.pdf", "document.pdf", "application/pdf")),
            )
    }

    @Test
    fun `mapToBaseNote note with audio`() {
        val actual =
            createEvernoteNote(
                    title = "Audio Note",
                    resources =
                        listOf(
                            EvernoteResource(
                                data =
                                    EvernoteResourceData(
                                        content =
                                            "/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBw4QDhAJDRAQGg0YEA8NBw"
                                    ),
                                width = 100,
                                height = 200,
                                mime = "audio/webm",
                                attributes = EvernoteResourceAttributes("audio.webm"),
                            )
                        ),
                )
                .mapToBaseNote()

        assertThat(actual)
            .has(Condition({ it.title == "Audio Note" }, "Title"))
            .has(Condition({ it.audios[0].name == "audio.webm" }, ""))
    }
}

private fun String.containsAll(vararg s: String): Boolean {
    s.forEach { if (!contains(it)) return false }
    return true
}

private fun createEvernoteNote(
    title: String = "",
    created: String = "",
    updated: String = "",
    tag: List<String> = listOf(),
    content: String = "",
    resources: List<EvernoteResource> = listOf(),
    tasks: List<EvernoteTask> = listOf(),
): EvernoteNote {
    return EvernoteNote(
        title,
        created,
        updated,
        tag.map { EvernoteTag(it) },
        content,
        resources,
        tasks,
    )
}
