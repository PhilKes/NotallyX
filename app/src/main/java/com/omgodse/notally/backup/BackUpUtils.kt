package com.omgodse.notally.backup

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.omgodse.notally.BackupProgress
import com.omgodse.notally.miscellaneous.Export
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.miscellaneous.Operations
import com.omgodse.notally.model.Converters
import com.omgodse.notally.model.NotallyDatabase
import java.io.OutputStream
import java.util.zip.ZipOutputStream

fun doBackup(
    outputStream: OutputStream,
    app: Application,
    backupProgress: MutableLiveData<BackupProgress>? = null,
) {
    val zipStream = ZipOutputStream(outputStream)

    val database = NotallyDatabase.getDatabase(app)

    database.checkpoint()
    Export.backupDatabase(app, zipStream)

    val imageRoot = IO.getExternalImagesDirectory(app)
    val fileRoot = IO.getExternalFilesDirectory(app)
    val audioRoot = IO.getExternalAudioDirectory(app)

    val images = database.getBaseNoteDao().getAllImages()
    val files = database.getBaseNoteDao().getAllFiles()
    val audios = database.getBaseNoteDao().getAllAudios()
    val total = images.size + files.size + audios.size
    images
        .asSequence()
        .flatMap { string -> Converters.jsonToFiles(string) }
        .forEachIndexed { index, image ->
            try {
                Export.backupFile(zipStream, imageRoot, "Images", image.localName)
            } catch (exception: Exception) {
                Operations.log(app, exception)
            } finally {
                backupProgress?.postValue(BackupProgress(true, index + 1, total, false))
            }
        }
    files
        .asSequence()
        .flatMap { string -> Converters.jsonToFiles(string) }
        .forEachIndexed { index, file ->
            try {
                Export.backupFile(zipStream, fileRoot, "Files", file.localName)
            } catch (exception: Exception) {
                Operations.log(app, exception)
            } finally {
                backupProgress?.postValue(
                    BackupProgress(true, images.size + index + 1, total, false)
                )
            }
        }
    audios
        .asSequence()
        .flatMap { string -> Converters.jsonToAudios(string) }
        .forEachIndexed { index, audio ->
            try {
                Export.backupFile(zipStream, audioRoot, "Audios", audio.name)
            } catch (exception: Exception) {
                Operations.log(app, exception)
            } finally {
                backupProgress?.postValue(
                    BackupProgress(true, images.size + files.size + index + 1, total, false)
                )
            }
        }

    zipStream.close()
}
