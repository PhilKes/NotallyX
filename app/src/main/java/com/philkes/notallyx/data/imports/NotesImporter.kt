package com.philkes.notallyx.data.imports

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.philkes.notallyx.R
import com.philkes.notallyx.data.DataUtil
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.evernote.EvernoteImporter
import com.philkes.notallyx.data.imports.google.GoogleKeepImporter
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import java.io.File

class NotesImporter(private val app: Application, private val database: NotallyDatabase) {

    suspend fun import(uri: Uri, importSource: ImportSource) {
        val (notes, importDataFolder) =
            when (importSource) {
                ImportSource.GOOGLE_KEEP -> GoogleKeepImporter().importFrom(uri, app)
                ImportSource.EVERNOTE -> EvernoteImporter().importFrom(uri, app)
            }
        database.getLabelDao().insert(notes.flatMap { it.labels }.distinct().map { Label(it) })
        importFiles(
            notes.flatMap { it.files }.distinct(),
            importDataFolder,
            NotallyModel.FileType.ANY,
        )
        importFiles(
            notes.flatMap { it.images }.distinct(),
            importDataFolder,
            NotallyModel.FileType.IMAGE,
        )
        importAudios(notes.flatMap { it.audios }.distinct(), importDataFolder)
        database.getBaseNoteDao().insert(notes)
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
            originalAudio.duration = if (audio.duration == 0L) null else audio.duration
            originalAudio.timestamp = audio.timestamp
        }
    }

    companion object {
        private const val TAG = "NotesImporter"
    }
}

enum class ImportSource(
    val folderName: String,
    val displayNameResId: Int,
    val mimeType: String,
    val helpTextResId: Int,
    val documentationUrl: String,
    val iconResId: Int,
) {
    GOOGLE_KEEP(
        "googlekeep",
        R.string.google_keep,
        "application/zip",
        R.string.google_keep_help,
        "https://support.google.com/keep/answer/10017039",
        R.drawable.icon_google_keep,
    ),
    EVERNOTE(
        "evernote",
        R.string.evernote,
        "*/*", // 'application/enex+xml' is not recognized
        R.string.evernote_help,
        "https://help.evernote.com/hc/en-us/articles/209005557-Export-notes-and-notebooks-as-ENEX-or-HTML",
        R.drawable.icon_evernote,
    ),
}

data class NotesImport(
    val baseNotes: List<BaseNote>,
    val labels: List<Label>,
    val files: List<FileAttachment>,
    val images: List<FileAttachment>,
    val audios: List<Audio>,
)
