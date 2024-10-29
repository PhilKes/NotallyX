package com.philkes.notallyx.data.imports.google

import android.app.Application
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.NotesImport
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json

class GoogleKeepImporter : ExternalImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        val destFile = File(destinationDir, zipEntry.name)

        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }

        return destFile
    }

    private fun unzip(destinationPath: File, inputStream: InputStream): File {
        val buffer = ByteArray(1024)
        val zis = ZipInputStream(inputStream)
        var zipEntry = zis.nextEntry
        while (zipEntry != null) {
            val newFile: File = newFile(destinationPath, zipEntry)
            if (zipEntry.isDirectory) {
                if (!newFile.isDirectory && !newFile.mkdirs()) {
                    throw IOException("Failed to create directory $newFile")
                }
            } else {
                val parent = newFile.parentFile
                if (parent != null) {
                    if (!parent.isDirectory && !parent.mkdirs()) {
                        throw IOException("Failed to create directory $parent")
                    }
                }
                val fos = FileOutputStream(newFile)
                var len: Int
                while ((zis.read(buffer).also { len = it }) > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.nextEntry
        }

        zis.closeEntry()
        zis.close()
        return File(destinationPath, "Takeout/Keep")
    }

    override fun importFrom(inputStream: InputStream, app: Application): Pair<NotesImport, File> {
        val tempDir = File(app.cacheDir, "google_keep")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val dataFolder =
            try {
                unzip(tempDir, inputStream)
            } catch (e: Exception) {
                throw ImportException(R.string.invalid_google_keep, e)
            }

        if (!dataFolder.exists()) {
            throw ImportException(R.string.invalid_google_keep)
        }

        val labels = mutableListOf<Label>()
        val labelsFile = File(dataFolder, "Labels.txt")
        if (labelsFile.exists()) {
            labels.addAll(labelsFile.readLines().map { Label(it) })
        }
        val baseNotes = mutableListOf<BaseNote>()
        val files = mutableListOf<FileAttachment>()
        val images = mutableListOf<FileAttachment>()
        val audios = mutableListOf<Audio>()
        dataFolder.walk().forEach { importFile ->
            if (importFile.extension == "json") {
                val noteImport = parseToBaseNote(importFile.readText())
                baseNotes.add(noteImport)
                files.addAll(noteImport.files)
                images.addAll(noteImport.images)
                audios.addAll(noteImport.audios)
            }
        }
        return Pair(NotesImport(baseNotes, labels, files, images, audios), dataFolder)
    }

    fun parseToBaseNote(jsonString: String): BaseNote {
        val keepNote = json.decodeFromString<KeepNote>(jsonString)

        val images =
            keepNote.attachments!!
                .filter { it.mimetype.startsWith("image") }
                .map { FileAttachment(it.filePath, it.filePath, it.mimetype) }

        val files =
            keepNote.attachments
                .filter { !it.mimetype.startsWith("audio") && !it.mimetype.startsWith("image") }
                .map { FileAttachment(it.filePath, it.filePath, it.mimetype) }

        val audios =
            keepNote.attachments
                .filter { it.mimetype.startsWith("audio") }
                .map { Audio(it.filePath, 0L, System.currentTimeMillis()) }

        val baseNote =
            BaseNote(
                id = 0L, // Auto-generated
                type = if (keepNote.listContent!!.isNotEmpty()) Type.LIST else Type.NOTE,
                folder =
                    when {
                        keepNote.isTrashed!! -> Folder.DELETED
                        keepNote.isArchived!! -> Folder.ARCHIVED
                        else -> Folder.NOTES
                    },
                color = Color.DEFAULT, // Ignoring color mapping
                title = keepNote.title!!,
                pinned = keepNote.isPinned!!,
                timestamp = keepNote.createdTimestampUsec!! / 1000,
                modifiedTimestamp = keepNote.userEditedTimestampUsec!! / 1000,
                labels = keepNote.labels!!.map { it.name },
                body = keepNote.textContent!!,
                spans =
                    mutableListOf(), // TODO: spans are only  in the textContentHtml in obfuscated
                // css/html, could use jsoup or similar
                items =
                    keepNote.listContent.mapIndexed { index, item ->
                        ListItem(
                            body = item.text,
                            checked = item.isChecked,
                            isChild =
                                false, // Google Keep doesn't have explicit child/indentation info
                            order = index,
                            children = mutableListOf(),
                        )
                    },
                images = images,
                files = files,
                audios = audios,
            )
        return baseNote
    }
}
