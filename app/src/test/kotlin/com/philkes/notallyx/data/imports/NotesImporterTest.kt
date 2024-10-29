package com.philkes.notallyx.data.imports

import android.app.Application
import android.os.Environment
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.utils.IO.decodeToBitmap
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
class NotesImporterTest {
    private lateinit var application: Application
    private lateinit var database: NotallyDatabase

    @Before
    fun setup() {
        application = ApplicationProvider.getApplicationContext()
        application.getExternalFilesDir(Environment.MEDIA_MOUNTED)
        database =
            Room.inMemoryDatabaseBuilder(application, NotallyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @Test
    fun `importFiles Google Keep`() {
        testImport(ImportSource.GOOGLE_KEEP, 8)
    }

    @Test
    fun `importFiles Evernote`() {
        // Evernote does not export archived and deleted notes
        testImport(ImportSource.EVERNOTE, 6)
    }

    private fun testImport(importSource: ImportSource, expectedAmountNotes: Int) {
        val importSourceFile = prepareImportSources(importSource)
        val importOutputFolder = prepareMediaFolder()
        runBlocking {
            NotesImporter(application, database).import(importSourceFile.toUri(), importSource)

            val actual = database.getBaseNoteDao().getAll().sortedBy { it.title }
            assertThat(actual).hasSize(expectedAmountNotes)
            actual.forEach { note ->
                note.images.forEach {
                    val imageFile =
                        File(
                            importOutputFolder,
                            "Android/media/com.philkes.notallyx.debug/Images/${it.localName}",
                        )
                    assertThat(imageFile)
                        .exists()
                        .`is`(
                            Condition(
                                { file ->
                                    val bitmap = file.decodeToBitmap()
                                    bitmap != null && bitmap.width == 1200 && bitmap.height == 1600
                                },
                                "Image",
                            )
                        )
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
                    val audioFile =
                        File(
                            importOutputFolder,
                            "Android/media/com.philkes.notallyx.debug/Audios/${it.name}",
                        )
                    assertThat(audioFile).exists()
                    // TODO: Metadata is not properly stored
                    //                        .`is`(
                    //                            Condition(
                    //                                { file ->
                    //                                    val retriever = MediaMetadataRetriever()
                    //                                    retriever.setDataSource(file.absolutePath)
                    //                                    val hasAudio =
                    //                                        retriever.extractMetadata(
                    //
                    // MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                    //                                        )
                    //                                    hasAudio != null
                    //                                },
                    //                                "Audio",
                    //                            )
                    //                        )
                }
            }
        }
    }

    private fun prepareMediaFolder(): String {
        val dir =
            File.createTempFile("notallyxNotesImporterTest", NotesImporter.IMPORT_CACHE_FOLDER)
                .apply {
                    delete()
                    mkdirs()
                }
        val path = dir.toPath().toString()
        ShadowEnvironment.addExternalDir(path)
        println("Output folder: $path")
        return path
    }

    private fun prepareImportSources(importSource: ImportSource): File {
        val tempDir = Files.createTempDirectory("imports-${importSource.name.lowercase()}").toFile()
        copyTestFilesToTempDir("imports/${importSource.name.lowercase()}", tempDir)
        println("Input folder: ${tempDir.absolutePath}")
        return when (importSource) {
            ImportSource.GOOGLE_KEEP -> File(tempDir, "Takeout.zip")
            ImportSource.EVERNOTE -> File(tempDir, "Notebook.enex")
        }
    }

    private fun copyTestFilesToTempDir(resourceFolderPath: String, destination: File) {
        val files =
            javaClass.classLoader!!.getResources(resourceFolderPath).toList().flatMap { url ->
                File(url.toURI()).listFiles()?.toList() ?: listOf()
            }
        files
            .filter { !it.isDirectory }
            .forEach { file ->
                val outputFile = File(destination, file.name)
                file.inputStream().use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
    }
}
