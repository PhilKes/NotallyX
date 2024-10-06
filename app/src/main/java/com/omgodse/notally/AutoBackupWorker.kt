package com.omgodse.notally

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.omgodse.notally.miscellaneous.Export
import com.omgodse.notally.miscellaneous.IO
import com.omgodse.notally.miscellaneous.Operations
import com.omgodse.notally.model.Converters
import com.omgodse.notally.model.NotallyDatabase
import com.omgodse.notally.preferences.AutoBackup
import com.omgodse.notally.preferences.Preferences
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipOutputStream

class AutoBackupWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val app = context.applicationContext as Application
        val preferences = Preferences.getInstance(app)
        val backupPath = preferences.autoBackup.value

        if (backupPath != AutoBackup.emptyPath) {
            val uri = Uri.parse(backupPath)
            val folder = requireNotNull(DocumentFile.fromTreeUri(app, uri))

            if (folder.exists()) {
                val formatter =
                    SimpleDateFormat("yyyyMMdd HHmmss '(Notally Backup)'", Locale.ENGLISH)
                val name = formatter.format(System.currentTimeMillis())
                val zipFile = requireNotNull(folder.createFile("application/zip", name))
                val outputStream = requireNotNull(app.contentResolver.openOutputStream(zipFile.uri))

                val zipStream = ZipOutputStream(outputStream)

                val database = NotallyDatabase.getDatabase(app)
                database.checkpoint()

                Export.backupDatabase(app, zipStream)

                val imageRoot = IO.getExternalImagesDirectory(app)
                val fileRoot = IO.getExternalFilesDirectory(app)
                val audioRoot = IO.getExternalAudioDirectory(app)
                database
                    .getBaseNoteDao()
                    .getAllImages()
                    .asSequence()
                    .flatMap { string -> Converters.jsonToFiles(string) }
                    .forEach { image ->
                        try {
                            Export.backupFile(zipStream, imageRoot, "Images", image.localName)
                        } catch (exception: Exception) {
                            Operations.log(app, exception)
                        }
                    }
                database
                    .getBaseNoteDao()
                    .getAllFiles()
                    .asSequence()
                    .flatMap { string -> Converters.jsonToFiles(string) }
                    .forEach { file ->
                        try {
                            Export.backupFile(zipStream, fileRoot, "Files", file.localName)
                        } catch (exception: Exception) {
                            Operations.log(app, exception)
                        }
                    }
                database
                    .getBaseNoteDao()
                    .getAllAudios()
                    .asSequence()
                    .flatMap { string -> Converters.jsonToAudios(string) }
                    .forEach { audio ->
                        try {
                            Export.backupFile(zipStream, audioRoot, "Audios", audio.name)
                        } catch (exception: Exception) {
                            Operations.log(app, exception)
                        }
                    }

                zipStream.close()
            }
        }

        return Result.success()
    }
}
