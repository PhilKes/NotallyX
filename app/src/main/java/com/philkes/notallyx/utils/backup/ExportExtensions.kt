package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.print.PdfPrintListener
import android.print.printPdf
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.toHtml
import com.philkes.notallyx.data.model.toJson
import com.philkes.notallyx.data.model.toTxt
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.SUBFOLDER_AUDIOS
import com.philkes.notallyx.utils.SUBFOLDER_FILES
import com.philkes.notallyx.utils.SUBFOLDER_IMAGES
import com.philkes.notallyx.utils.getExportedPath
import com.philkes.notallyx.utils.getExternalAudioDirectory
import com.philkes.notallyx.utils.getExternalFilesDirectory
import com.philkes.notallyx.utils.getExternalImagesDirectory
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.removeTrailingParentheses
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod

private const val TAG = "ExportExtensions"
private const val AUTO_BACKUP_WORK_NAME = "Auto Backup"

fun ContextWrapper.exportAsZip(fileUri: Uri, backupProgress: MutableLiveData<Progress>? = null) {
    backupProgress?.postValue(Progress(indeterminate = true))
    val preferences = NotallyXPreferences.getInstance(this)
    val backupPassword = preferences.backupPassword.value

    val tempFile = File.createTempFile("export", "tmp", cacheDir)
    val zipFile =
        ZipFile(
            tempFile,
            if (backupPassword != PASSWORD_EMPTY) backupPassword.toCharArray() else null,
        )
    val zipParameters =
        ZipParameters().apply {
            isEncryptFiles = backupPassword != PASSWORD_EMPTY
            compressionLevel = CompressionLevel.NO_COMPRESSION
            encryptionMethod = EncryptionMethod.AES
        }

    val (databaseOriginal, databaseCopy) = copyDatabase()
    zipFile.addFile(databaseCopy, zipParameters.copy(NotallyDatabase.DatabaseName))
    databaseCopy.delete()

    val imageRoot = getExternalImagesDirectory()
    val fileRoot = getExternalFilesDirectory()
    val audioRoot = getExternalAudioDirectory()

    val images = databaseOriginal.getBaseNoteDao().getAllImages().toFileAttachments()
    val files = databaseOriginal.getBaseNoteDao().getAllFiles().toFileAttachments()
    val audios = databaseOriginal.getBaseNoteDao().getAllAudios()
    val total = images.count() + files.count() + audios.size
    backupProgress?.postValue(Progress(0, total))

    val counter = AtomicInteger(0)
    images.export(
        zipFile,
        zipParameters,
        imageRoot,
        SUBFOLDER_IMAGES,
        this,
        backupProgress,
        total,
        counter,
    )
    files.export(
        zipFile,
        zipParameters,
        fileRoot,
        SUBFOLDER_FILES,
        this,
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
                log(TAG, throwable = exception)
            } finally {
                backupProgress?.postValue(Progress(counter.incrementAndGet(), total))
            }
        }

    zipFile.close()
    contentResolver.openOutputStream(fileUri)?.use { outputStream ->
        FileInputStream(zipFile.file).use { inputStream ->
            inputStream.copyTo(outputStream)
            outputStream.flush()
        }
        zipFile.file.delete()
    }
    backupProgress?.postValue(Progress(inProgress = false))
}

private fun ContextWrapper.copyDatabase(): Pair<NotallyDatabase, File> {
    val database = NotallyDatabase.getDatabase(this, observePreferences = false).value
    database.checkpoint()
    val preferences = NotallyXPreferences.getInstance(this)
    return if (
        preferences.biometricLock.value == BiometricLock.ENABLED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    ) {
        val cipher = getInitializedCipherForDecryption(iv = preferences.iv.value!!)
        val passphrase = cipher.doFinal(preferences.databaseEncryptionKey.value)
        val decryptedFile = File.createTempFile("decrypted", "tmp", cacheDir)
        decryptDatabase(
            this,
            passphrase,
            decryptedFile,
            NotallyDatabase.getCurrentDatabaseName(this),
        )
        Pair(database, decryptedFile)
    } else {
        val dbFile = File.createTempFile("database", "tmp", cacheDir)
        NotallyDatabase.getCurrentDatabaseFile(this).copyTo(dbFile, overwrite = true)
        Pair(database, dbFile)
    }
}

private fun List<String>.toFileAttachments(): Sequence<FileAttachment> {
    return asSequence().flatMap { string -> Converters.jsonToFiles(string) }
}

private fun Sequence<FileAttachment>.export(
    zipFile: ZipFile,
    zipParameters: ZipParameters,
    fileRoot: File?,
    subfolder: String,
    context: ContextWrapper,
    backupProgress: MutableLiveData<Progress>?,
    total: Int,
    counter: AtomicInteger,
) {
    forEach { file ->
        try {
            backupFile(zipFile, zipParameters, fileRoot, subfolder, file.localName)
        } catch (exception: Exception) {
            context.log(TAG, throwable = exception)
        } finally {
            backupProgress?.postValue(Progress(counter.incrementAndGet(), total))
        }
    }
}

fun Context.scheduleAutoBackup(periodInDays: Long) {
    val request =
        PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, periodInDays, TimeUnit.DAYS)
            .build()
    try {
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                AUTO_BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    } catch (e: IllegalStateException) {
        // only happens in Unit-Tests
    }
}

fun cancelAutoBackup(context: Context) {
    try {
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
    } catch (e: IllegalStateException) {
        // only happens in Unit-Tests
    }
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

fun exportPdfFile(
    app: Application,
    note: BaseNote,
    folder: DocumentFile,
    fileName: String = note.title,
    pdfPrintListener: PdfPrintListener? = null,
    progress: MutableLiveData<Progress>? = null,
    counter: AtomicInteger? = null,
    total: Int? = null,
    duplicateFileCount: Int = 1,
) {
    val filePath = "$fileName.${ExportMimeType.PDF.fileExtension}"
    if (folder.findFile(filePath)?.exists() == true) {
        return exportPdfFile(
            app,
            note,
            folder,
            "${fileName.removeTrailingParentheses()} ($duplicateFileCount)",
            pdfPrintListener,
            progress,
            counter,
            total,
            duplicateFileCount + 1,
        )
    }
    folder.createFile(ExportMimeType.PDF.mimeType, fileName)?.let {
        val file = DocumentFile.fromFile(File(app.getExportedPath(), filePath))
        val html = note.toHtml(NotallyXPreferences.getInstance(app).showDateCreated())
        app.printPdf(
            file,
            html,
            object : PdfPrintListener {
                override fun onSuccess(file: DocumentFile) {
                    app.contentResolver.openOutputStream(it.uri)?.use { outStream ->
                        app.contentResolver.openInputStream(file.uri)?.copyTo(outStream)
                    }
                    progress?.postValue(
                        Progress(current = counter!!.incrementAndGet(), total = total!!)
                    )
                    pdfPrintListener?.onSuccess(file)
                }

                override fun onFailure(message: CharSequence?) {
                    pdfPrintListener?.onFailure(message)
                }
            },
        )
    }
}

suspend fun exportPlainTextFile(
    app: Application,
    note: BaseNote,
    exportType: ExportMimeType,
    folder: DocumentFile,
    fileName: String = note.title,
    progress: MutableLiveData<Progress>? = null,
    counter: AtomicInteger? = null,
    total: Int? = null,
    duplicateFileCount: Int = 1,
): DocumentFile? {
    if (folder.findFile("$fileName.${exportType.fileExtension}")?.exists() == true) {
        return exportPlainTextFile(
            app,
            note,
            exportType,
            folder,
            "${fileName.removeTrailingParentheses()} ($duplicateFileCount)",
            progress,
            counter,
            total,
            duplicateFileCount + 1,
        )
    }
    return withContext(Dispatchers.IO) {
        val file =
            folder.createFile(exportType.mimeType, fileName)?.let {
                app.contentResolver.openOutputStream(it.uri)?.use { stream ->
                    OutputStreamWriter(stream).use { writer ->
                        writer.write(
                            when (exportType) {
                                ExportMimeType.TXT ->
                                    note.toTxt(includeTitle = false, includeCreationDate = false)

                                ExportMimeType.JSON -> note.toJson()
                                ExportMimeType.HTML ->
                                    note.toHtml(
                                        NotallyXPreferences.getInstance(app).showDateCreated()
                                    )

                                else -> TODO("Unsupported MimeType for Export")
                            }
                        )
                    }
                }
                it
            }
        progress?.postValue(Progress(current = counter!!.incrementAndGet(), total = total!!))
        return@withContext file
    }
}

fun Context.exportPreferences(preferences: NotallyXPreferences, uri: Uri): Boolean {
    try {
        contentResolver.openOutputStream(uri)?.use {
            it.write(preferences.toJsonString().toByteArray())
        } ?: return false
        return true
    } catch (e: IOException) {
        if (this is ContextWrapper) {
            log(TAG, throwable = e)
        } else {
            Log.e(TAG, "Export preferences failed", e)
        }
        return false
    }
}
