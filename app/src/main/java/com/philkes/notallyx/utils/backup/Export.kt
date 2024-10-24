package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.presentation.view.misc.BackupPassword.emptyPassword
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.utils.IO.SUBFOLDER_AUDIOS
import com.philkes.notallyx.utils.IO.SUBFOLDER_FILES
import com.philkes.notallyx.utils.IO.SUBFOLDER_IMAGES
import com.philkes.notallyx.utils.IO.getExternalAudioDirectory
import com.philkes.notallyx.utils.IO.getExternalFilesDirectory
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod

object Export {

    fun exportAsZip(
        fileUri: Uri,
        app: Application,
        backupProgress: MutableLiveData<BackupProgress>? = null,
    ) {
        val database = NotallyDatabase.getDatabase(app).value
        database.checkpoint()
        val preferences = Preferences.getInstance(app)
        val backupPassword = preferences.backupPassword.value

        val tempFile = File.createTempFile("export", "tmp", app.cacheDir)
        val zipFile =
            ZipFile(
                tempFile,
                if (backupPassword != emptyPassword) backupPassword.toCharArray() else null,
            )
        val zipParameters =
            ZipParameters().apply {
                isEncryptFiles = backupPassword != emptyPassword
                compressionLevel = CompressionLevel.NO_COMPRESSION
                encryptionMethod = EncryptionMethod.AES
            }

        if (
            preferences.biometricLock.value == enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        ) {
            val cipher = getInitializedCipherForDecryption(iv = preferences.iv!!)
            val passphrase = cipher.doFinal(preferences.getDatabasePassphrase())
            val decryptedFile = File.createTempFile("decrypted", "tmp", app.cacheDir)
            decryptDatabase(app, passphrase, decryptedFile)
            zipFile.addFile(decryptedFile, zipParameters.copy(NotallyDatabase.DatabaseName))
            decryptedFile.delete()
        } else {
            zipFile.addFile(
                app.getDatabasePath(NotallyDatabase.DatabaseName),
                zipParameters.copy(NotallyDatabase.DatabaseName),
            )
        }

        val imageRoot = app.getExternalImagesDirectory()
        val fileRoot = app.getExternalFilesDirectory()
        val audioRoot = app.getExternalAudioDirectory()

        val images = database.getBaseNoteDao().getAllImages()
        val files = database.getBaseNoteDao().getAllFiles()
        val audios = database.getBaseNoteDao().getAllAudios()
        val total = images.size + files.size + audios.size

        val counter = AtomicInteger(0)
        exportFileAttachments(
            images,
            zipFile,
            zipParameters,
            imageRoot,
            SUBFOLDER_IMAGES,
            app,
            backupProgress,
            total,
            counter,
        )
        exportFileAttachments(
            files,
            zipFile,
            zipParameters,
            fileRoot,
            SUBFOLDER_FILES,
            app,
            backupProgress,
            total,
            counter,
        )
        audios
            .asSequence()
            .flatMap { string -> Converters.jsonToAudios(string) }
            .forEach { audio ->
                try {
                    backupFile(zipFile, zipParameters, audioRoot, SUBFOLDER_AUDIOS, audio.name)
                } catch (exception: Exception) {
                    Operations.log(app, exception)
                } finally {
                    backupProgress?.postValue(
                        BackupProgress(true, counter.incrementAndGet(), total, false)
                    )
                }
            }

        zipFile.close()
        app.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
            FileInputStream(zipFile.file).use { inputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            }
            zipFile.file.delete()
        }
    }

    private fun exportFileAttachments(
        files: List<String>,
        zipFile: ZipFile,
        zipParameters: ZipParameters,
        fileRoot: File?,
        subfolder: String,
        app: Application,
        backupProgress: MutableLiveData<BackupProgress>?,
        total: Int,
        counter: AtomicInteger,
    ) {
        files
            .asSequence()
            .flatMap { string -> Converters.jsonToFiles(string) }
            .forEach { file ->
                try {
                    backupFile(zipFile, zipParameters, fileRoot, subfolder, file.localName)
                } catch (exception: Exception) {
                    Operations.log(app, exception)
                } finally {
                    backupProgress?.postValue(
                        BackupProgress(true, counter.incrementAndGet(), total, false)
                    )
                }
            }
    }

    fun scheduleAutoBackup(periodInDays: Long, context: Context) {
        val request =
            PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, periodInDays, TimeUnit.DAYS)
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("Auto Backup", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun backupFile(
        zipFile: ZipFile,
        zipParameters: ZipParameters,
        root: File?,
        folder: String,
        name: String,
    ) {
        val file = if (root != null) File(root, name) else null
        if (file != null && file.exists()) {
            zipFile.addFile(file, zipParameters.copy("$folder/$name"))
        }
    }

    private fun ZipParameters.copy(fileNameInZip: String? = this.fileNameInZip): ZipParameters {
        return ZipParameters(this).apply { this@apply.fileNameInZip = fileNameInZip }
    }
}
