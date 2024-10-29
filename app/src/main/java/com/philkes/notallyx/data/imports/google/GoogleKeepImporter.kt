package com.philkes.notallyx.data.imports.google

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.imports.parseBodyAndSpansFromHtml
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

class GoogleKeepImporter : ExternalImporter {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    override fun import(
        app: Application,
        source: Uri,
        destination: File,
        progress: MutableLiveData<ImportProgress>?,
    ): Pair<List<BaseNote>, File> {
        progress?.postValue(ImportProgress(indeterminate = true, stage = ImportStage.EXTRACT_FILES))
        val dataFolder =
            try {
                unzip(destination, app.contentResolver.openInputStream(source)!!)
            } catch (e: Exception) {
                throw ImportException(R.string.invalid_google_keep, e)
            }

        if (!dataFolder.exists()) {
            throw ImportException(
                R.string.invalid_google_keep,
                RuntimeException("Extracting Takeout.zip failed"),
            )
        }

        val noteFiles =
            dataFolder
                .listFiles { file ->
                    file.isFile && file.extension.equals("json", ignoreCase = true)
                }
                ?.toList() ?: emptyList()
        val total = noteFiles.size
        progress?.postValue(ImportProgress(0, total, stage = ImportStage.IMPORT_NOTES))
        var counter = 1
        val baseNotes =
            noteFiles
                .mapNotNull { file ->
                    val baseNote = file.readText().parseToBaseNote()
                    progress?.postValue(
                        ImportProgress(counter++, total, stage = ImportStage.IMPORT_NOTES)
                    )
                    baseNote
                }
                .toList()
        return Pair(baseNotes, dataFolder)
    }

    fun String.parseToBaseNote(): BaseNote {
        val googleKeepNote = json.decodeFromString<GoogleKeepNote>(this)
        val (body, spans) =
            parseBodyAndSpansFromHtml(
                googleKeepNote.textContentHtml,
                paragraphsAsNewLine = true,
                brTagsAsNewLine = true,
            )

        val images =
            googleKeepNote.attachments
                .filter { it.mimetype.startsWith("image") }
                .map { FileAttachment(it.filePath, it.filePath, it.mimetype) }
        val files =
            googleKeepNote.attachments
                .filter { !it.mimetype.startsWith("audio") && !it.mimetype.startsWith("image") }
                .map { FileAttachment(it.filePath, it.filePath, it.mimetype) }
        val audios =
            googleKeepNote.attachments
                .filter { it.mimetype.startsWith("audio") }
                .map { Audio(it.filePath, 0L, System.currentTimeMillis()) }
        val items =
            googleKeepNote.listContent.mapIndexed { index, item ->
                ListItem(
                    body = item.text,
                    checked = item.isChecked,
                    isChild = false, // Google Keep doesn't have explicit child/indentation info
                    order = index,
                    children = mutableListOf(),
                )
            }

        return BaseNote(
            id = 0L, // Auto-generated
            type = if (googleKeepNote.listContent.isNotEmpty()) Type.LIST else Type.NOTE,
            folder =
                when {
                    googleKeepNote.isTrashed -> Folder.DELETED
                    googleKeepNote.isArchived -> Folder.ARCHIVED
                    else -> Folder.NOTES
                },
            color = Color.DEFAULT, // Ignoring color mapping
            title = googleKeepNote.title,
            pinned = googleKeepNote.isPinned,
            timestamp = googleKeepNote.createdTimestampUsec / 1000,
            modifiedTimestamp = googleKeepNote.userEditedTimestampUsec / 1000,
            labels = googleKeepNote.labels.map { it.name },
            body = body,
            spans = spans,
            items = items,
            images = images,
            files = files,
            audios = audios,
        )
    }

    private fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
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
}
