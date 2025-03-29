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
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.utils.listFilesRecursive
import com.philkes.notallyx.utils.log
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
                app.contentResolver.openInputStream(source)!!.use { unzip(destination, it) }
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
                .listFilesRecursive { file ->
                    file.isFile && file.extension.equals("json", ignoreCase = true)
                }
                .toList()
        val total = noteFiles.size
        progress?.postValue(ImportProgress(0, total, stage = ImportStage.IMPORT_NOTES))
        var counter = 1
        val baseNotes =
            noteFiles
                .mapNotNull { file ->
                    val baseNote =
                        try {
                            val relativePath = file.parentFile!!.toRelativeString(dataFolder)
                            file.readText().parseToBaseNote(relativePath)
                        } catch (e: Exception) {
                            app.log(
                                TAG,
                                msg =
                                    "Could not parse BaseNote from JSON in file '${file.absolutePath}'",
                                throwable = e,
                            )
                            null
                        }
                    progress?.postValue(
                        ImportProgress(counter++, total, stage = ImportStage.IMPORT_NOTES)
                    )
                    baseNote
                }
                .toList()
        return Pair(baseNotes, dataFolder)
    }

    fun String.parseToBaseNote(relativePath: String? = null): BaseNote {
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
                .map { attachment ->
                    FileAttachment(
                        "${relativePath?.let { "$it/" } ?: ""}${attachment.filePath}",
                        attachment.filePath,
                        attachment.mimetype,
                    )
                }
        val files =
            googleKeepNote.attachments
                .filter { !it.mimetype.startsWith("audio") && !it.mimetype.startsWith("image") }
                .map { attachment ->
                    FileAttachment(
                        "${relativePath?.let { "$it/" } ?: ""}${attachment.filePath}",
                        attachment.filePath,
                        attachment.mimetype,
                    )
                }
        val audios =
            googleKeepNote.attachments
                .filter { it.mimetype.startsWith("audio") }
                .map { attachment ->
                    Audio(
                        "${relativePath?.let { "$it/" } ?: ""}${attachment.filePath}",
                        0L,
                        System.currentTimeMillis(),
                    )
                }
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
            color = BaseNote.COLOR_DEFAULT, // Ignoring color mapping
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
            reminders = mutableListOf(),
            NoteViewMode.EDIT,
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
                FileOutputStream(newFile).use {
                    var len: Int
                    while ((zis.read(buffer).also { length -> len = length }) > 0) {
                        it.write(buffer, 0, len)
                    }
                }
            }
            zipEntry = zis.nextEntry
        }

        zis.closeEntry()
        zis.close()
        return destinationPath
    }

    companion object {
        private const val TAG = "GoogleKeepImporter"
    }
}
