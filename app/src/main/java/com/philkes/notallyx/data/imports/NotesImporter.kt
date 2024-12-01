package com.philkes.notallyx.data.imports

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.DataUtil
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.evernote.EvernoteImporter
import com.philkes.notallyx.data.imports.google.GoogleKeepImporter
import com.philkes.notallyx.data.imports.txt.PlainTextImporter
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class NotesImporter(private val app: Application, private val database: NotallyDatabase) {

    suspend fun import(
        uri: Uri,
        importSource: ImportSource,
        progress: MutableLiveData<ImportProgress>? = null,
    ): Int {
        val tempDir = File(app.cacheDir, IMPORT_CACHE_FOLDER)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        try {
            val (notes, importDataFolder) =
                try {
                    when (importSource) {
                        ImportSource.GOOGLE_KEEP -> GoogleKeepImporter()
                        ImportSource.EVERNOTE -> EvernoteImporter()
                        ImportSource.PLAIN_TEXT -> PlainTextImporter()
                    }.import(app, uri, tempDir, progress)
                } catch (e: Exception) {
                    Log.e(TAG, "import: failed", e)
                    progress?.postValue(ImportProgress(inProgress = false))
                    throw e
                }
            database.getLabelDao().insert(notes.flatMap { it.labels }.distinct().map { Label(it) })
            val files = notes.flatMap { it.files }.distinct()
            val images = notes.flatMap { it.images }.distinct()
            val audios = notes.flatMap { it.audios }.distinct()
            val totalFiles = files.size + images.size + audios.size
            val counter = AtomicInteger(1)
            progress?.postValue(
                ImportProgress(total = totalFiles, stage = ImportStage.IMPORT_FILES)
            )
            importDataFolder?.let {
                importFiles(files, it, NotallyModel.FileType.ANY, progress, totalFiles, counter)
                importFiles(images, it, NotallyModel.FileType.IMAGE, progress, totalFiles, counter)
                importAudios(audios, it, progress, totalFiles, counter)
            }
            database.getBaseNoteDao().insert(notes)
            progress?.postValue(ImportProgress(inProgress = false))
            return notes.size
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun importFiles(
        files: List<FileAttachment>,
        sourceFolder: File,
        fileType: NotallyModel.FileType,
        progress: MutableLiveData<ImportProgress>?,
        total: Int?,
        counter: AtomicInteger?,
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
            error?.let { Log.e(TAG, "Failed to import: $error") }
            progress?.postValue(
                ImportProgress(
                    current = counter!!.getAndIncrement(),
                    total = total!!,
                    stage = ImportStage.IMPORT_FILES,
                )
            )
        }
    }

    private suspend fun importAudios(
        audios: List<Audio>,
        sourceFolder: File,
        progress: MutableLiveData<ImportProgress>?,
        totalFiles: Int,
        counter: AtomicInteger,
    ) {
        audios.forEach { originalAudio ->
            val file = File(sourceFolder, originalAudio.name)
            val audio = DataUtil.addAudio(app, file, false)
            originalAudio.name = audio.name
            originalAudio.duration = if (audio.duration == 0L) null else audio.duration
            originalAudio.timestamp = audio.timestamp
            progress?.postValue(
                ImportProgress(
                    current = counter.getAndIncrement(),
                    total = totalFiles,
                    stage = ImportStage.IMPORT_FILES,
                )
            )
        }
    }

    companion object {
        private const val TAG = "NotesImporter"
        const val IMPORT_CACHE_FOLDER = "imports"
    }
}

enum class ImportSource(
    val displayNameResId: Int,
    val mimeType: String,
    val helpTextResId: Int,
    val documentationUrl: String?,
    val iconResId: Int,
) {
    GOOGLE_KEEP(
        R.string.google_keep,
        "application/zip",
        R.string.google_keep_help,
        "https://support.google.com/keep/answer/10017039",
        R.drawable.icon_google_keep,
    ),
    EVERNOTE(
        R.string.evernote,
        "*/*", // 'application/enex+xml' is not recognized
        R.string.evernote_help,
        "https://help.evernote.com/hc/en-us/articles/209005557-Export-notes-and-notebooks-as-ENEX-or-HTML",
        R.drawable.icon_evernote,
    ),
    PLAIN_TEXT(
        R.string.plain_text_files,
        FOLDER_MIMETYPE,
        R.string.plain_text_files_help,
        null,
        R.drawable.text_file,
    ),
}

const val FOLDER_MIMETYPE = "FOLDER"
