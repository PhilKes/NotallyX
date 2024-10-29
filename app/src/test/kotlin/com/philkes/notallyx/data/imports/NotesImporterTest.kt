package com.philkes.notallyx.data.imports

import android.app.Application
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.google.GoogleKeepImporterTest.Companion.createBaseNote
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
class NotesImporterTest {

    @Test
    fun `importFiles Google Keep`() {
        val application: Application = ApplicationProvider.getApplicationContext()
        application.getExternalFilesDir(Environment.MEDIA_MOUNTED)

        val database =
            Room.inMemoryDatabaseBuilder(application, NotallyDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        val importSourceFile = prepareImportSources(ImportSource.GOOGLE_KEEP)
        val importOutputFolder = prepareMediaFolder(ImportSource.GOOGLE_KEEP)

        runBlocking {
            NotesImporter(application, database)
                .import(importSourceFile.inputStream(), ImportSource.GOOGLE_KEEP)
            println()
            val notes = database.getBaseNoteDao().getAll().sortedBy { it.title }
            var noteCounter = 0
            assertThat(notes)
                .hasSize(8)
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                    "id",
                    "audios.name",
                    "audios.timestamp",
                    "files.localName",
                )
                .containsAnyOf(
                    createBaseNote(
                        noteCounter++,
                        folder = Folder.ARCHIVED,
                        title = "Archived",
                        body = "This is archived",
                    ),
                    createBaseNote(
                        noteCounter++,
                        title = "Audio",
                        body = "This includes audio",
                        audios = listOf(Audio("", -1, 1)),
                    ),
                    createBaseNote(
                        noteCounter++,
                        folder = Folder.DELETED,
                        title = "Deleted",
                        body = "This is deleted",
                    ),
                    createBaseNote(
                        noteCounter++,
                        title = "File",
                        body = "This has a file",
                        files = listOf(FileAttachment("", "document.doc", "application/msword")),
                    ),
                    createBaseNote(
                        noteCounter++,
                        title = "Image",
                        body = "This has an image",
                        images = listOf(FileAttachment("", "image.jpg", "image/jpeg")),
                    ),
                    createBaseNote(
                        noteCounter++,
                        title = "Normal Note",
                        body = "This is some note, nothing special",
                    ),
                    createBaseNote(
                        noteCounter++,
                        type = Type.LIST,
                        title = "Pinned List",
                        pinned = true,
                        items =
                            listOf(
                                createListItem("Parent1", order = 0),
                                createListItem("Child1", order = 1),
                                createListItem("Child2", order = 2),
                                createListItem("Parent2", order = 3),
                                createListItem("Parent4", order = 4, checked = true),
                                createListItem("Parent3", order = 5, checked = true),
                                createListItem("Child4", order = 6, checked = true),
                            ),
                    ),
                    createBaseNote(
                        noteCounter++,
                        title = "Text formatting",
                        body =
                            "This is fat\n" +
                                "\n" +
                                "Heading 1\n" +
                                "\n" +
                                "Italic\n" +
                                "\n" +
                                "Underlined\n" +
                                "\n" +
                                "\n" +
                                "https://www.google.com/\n" +
                                "\n" +
                                "This is a link",
                    ),
                )
            notes.forEach { note ->
                note.images.forEach {
                    assertThat(
                            File(
                                importOutputFolder,
                                "Android/media/com.philkes.notallyx.debug/Images/${it.localName}",
                            )
                        )
                        .exists()
                }
                note.files.forEach {
                    assertThat(
                            File(
                                importOutputFolder,
                                "Android/media/com.philkes.notallyx.debug/Files/${it.localName}",
                            )
                        )
                        .exists()
                }
                note.audios.forEach {
                    assertThat(
                            File(
                                importOutputFolder,
                                "Android/media/com.philkes.notallyx.debug/Audios/${it.name}",
                            )
                        )
                        .exists()
                }
            }
        }
    }

    private fun createListItem(
        body: String,
        checked: Boolean = false,
        isChild: Boolean = false,
        order: Int?,
        children: MutableList<ListItem> = mutableListOf(),
    ): ListItem {
        return ListItem(body, checked, isChild, order, children)
    }

    private fun createBaseNote(
        counter: Int,
        id: Long = 0L,
        type: Type = Type.NOTE,
        folder: Folder = Folder.NOTES,
        color: Color = Color.DEFAULT,
        title: String = "Note",
        pinned: Boolean = false,
        labels: List<String> = listOf(),
        body: String = "",
        spans: List<SpanRepresentation> = listOf(),
        items: List<ListItem> = listOf(),
        images: List<FileAttachment> = listOf(),
        files: List<FileAttachment> = listOf(),
        audios: List<Audio> = listOf(),
    ): BaseNote {
        return createBaseNote(
            id,
            type,
            folder,
            color,
            title,
            pinned,
            "1${counter}00".toLong(),
            "1${counter}01".toLong(),
            labels,
            body,
            spans,
            items,
            images,
            files,
            audios,
        )
    }

    private fun prepareMediaFolder(importSource: ImportSource): String {
        val dir =
            File.createTempFile("notallyxNotesImporterTest", importSource.folderName).apply {
                delete()
                mkdirs()
            }
        val path = dir.toPath().toString()
        ShadowEnvironment.addExternalDir(path)
        println("Output folder: $path")
        return path
    }

    private fun prepareImportSources(importSource: ImportSource): File {
        val tempDir = Files.createTempDirectory("imports-${importSource.folderName}").toFile()
        copyTestFilesToTempDir("imports/${importSource.folderName}", tempDir)
        println("Input folder: ${tempDir.absolutePath}")
        return when (importSource) {
            ImportSource.GOOGLE_KEEP -> File(tempDir, "Takeout.zip")
            else -> tempDir
        }
    }

    private fun copyTestFilesToTempDir(resourceFolderPath: String, destination: File) {
        val files =
            javaClass.classLoader!!.getResources(resourceFolderPath).toList().flatMap { url ->
                File(url.toURI()).listFiles()?.toList() ?: listOf()
            }
        files.forEach { file ->
            val outputFile = File(destination, file.name)
            file.inputStream().use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
