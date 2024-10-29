package com.philkes.notallyx.data.imports

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import com.philkes.notallyx.R
import com.philkes.notallyx.data.DataUtil
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.google.GoogleKeepImporter
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import java.io.File
import java.io.InputStream

class NotesImporter(private val app: Application, private val database: NotallyDatabase) {

    suspend fun import(inputStream: InputStream, importSource: ImportSource) {
        val (import, importDataFolder) =
            when (importSource) {
                ImportSource.GOOGLE_KEEP -> GoogleKeepImporter().importFrom(inputStream, app)
            }
        database.getLabelDao().insert(import.labels)
        importFiles(import.files, importDataFolder, NotallyModel.FileType.ANY)
        importFiles(import.images, importDataFolder, NotallyModel.FileType.IMAGE)
        importAudios(import.audios, importDataFolder)
        database.getBaseNoteDao().insert(import.baseNotes)
    }

    private suspend fun importFiles(
        files: List<FileAttachment>,
        sourceFolder: File,
        fileType: NotallyModel.FileType,
    ) {
        files.forEach { file ->
            val uri = File(sourceFolder, file.localName).toUri()
            val (fileAttachment, error) =
                if (fileType == NotallyModel.FileType.IMAGE)
                    DataUtil.addImage(app, uri, file.mimeType)
                else DataUtil.addFile(app, uri, file.mimeType)
            fileAttachment?.let {
                file.localName = fileAttachment.localName
                file.originalName = fileAttachment.originalName
                file.mimeType = fileAttachment.mimeType
            }
            error?.let { Log.d(TAG, "Failed to import: $error") }
        }
    }

    private suspend fun importAudios(audios: List<Audio>, sourceFolder: File) {
        audios.forEach { originalAudio ->
            val file = File(sourceFolder, originalAudio.name)
            val audio = DataUtil.addAudio(app, file, false)
            originalAudio.name = audio.name
            originalAudio.duration = audio.duration
            originalAudio.timestamp = audio.timestamp
        }
    }

    companion object {
        private const val TAG = "NotesImporter"
    }
}

enum class ImportSource(
    val folderName: String,
    val displayName: String,
    val mimeType: String,
    val helpTextResId: Int,
    val documentationUrl: String,
) {
    GOOGLE_KEEP(
        "googlekeep",
        "Google Keep",
        "application/zip",
        R.string.google_keep_help,
        "https://support.google.com/keep/answer/10017039",
    )
}

data class NotesImport(
    val baseNotes: List<BaseNote>,
    val labels: List<Label>,
    val files: List<FileAttachment>,
    val images: List<FileAttachment>,
    val audios: List<Audio>,
)
