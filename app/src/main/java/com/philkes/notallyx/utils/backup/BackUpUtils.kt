package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.utils.IO
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipOutputStream

fun doBackup(
    outputStream: OutputStream,
    app: Application,
    backupProgress: MutableLiveData<BackupProgress>? = null,
) {
    val zipStream = ZipOutputStream(outputStream)

    val database = NotallyDatabase.getDatabase(app).value

    val preferences = Preferences.getInstance(app)

    database.checkpoint()

    if (
        preferences.biometricLock.value == enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    ) {
        val cipher = getInitializedCipherForDecryption(iv = preferences.iv!!)
        val passphrase = cipher.doFinal(preferences.getDatabasePassphrase())
        val decryptedFile = File.createTempFile("decrypted", "tmp", app.cacheDir)
        decryptDatabase(app, passphrase, decryptedFile)
        Export.backupDatabase(app, zipStream, decryptedFile)
        decryptedFile.delete()
    } else {
        Export.backupDatabase(app, zipStream)
    }

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

fun scheduleAutoBackup(periodInDays: Long, context: Context) {
    val request =
        PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, periodInDays, TimeUnit.DAYS)
            .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("Auto Backup", ExistingPeriodicWorkPolicy.UPDATE, request)
}
