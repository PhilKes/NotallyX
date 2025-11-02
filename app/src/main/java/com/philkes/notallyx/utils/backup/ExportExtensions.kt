package com.philkes.notallyx.utils.backup

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.print.PdfPrintListener
import android.print.printPdf
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.NotallyDatabase.Companion.DATABASE_NAME
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.toHtml
import com.philkes.notallyx.data.model.toJson
import com.philkes.notallyx.data.model.toTxt
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.activity.main.fragment.settings.SettingsFragment
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.viewmodel.BackupFile
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.progress.BackupProgress
import com.philkes.notallyx.utils.MIME_TYPE_ZIP
import com.philkes.notallyx.utils.SUBFOLDER_AUDIOS
import com.philkes.notallyx.utils.SUBFOLDER_FILES
import com.philkes.notallyx.utils.SUBFOLDER_IMAGES
import com.philkes.notallyx.utils.createChannelIfNotExists
import com.philkes.notallyx.utils.createFileSafe
import com.philkes.notallyx.utils.createReportBugIntent
import com.philkes.notallyx.utils.getExportedPath
import com.philkes.notallyx.utils.getExternalAudioDirectory
import com.philkes.notallyx.utils.getExternalFilesDirectory
import com.philkes.notallyx.utils.getExternalImagesDirectory
import com.philkes.notallyx.utils.getLogFileUri
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.listZipFiles
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.nameWithoutExtension
import com.philkes.notallyx.utils.recreateDir
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.getInitializedCipherForDecryption
import com.philkes.notallyx.utils.viewFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.EncryptionMethod

private const val TAG = "ExportExtensions"
private const val NOTIFICATION_CHANNEL_ID = "AutoBackups"
private const val NOTIFICATION_ID = 123412
private const val OUTPUT_DATA_BACKUP_URI = "backupUri"

const val AUTO_BACKUP_WORK_NAME = "com.philkes.notallyx.AutoBackupWork"
const val OUTPUT_DATA_EXCEPTION = "exception"

val BACKUP_TIMESTAMP_FORMATTER = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.ENGLISH)
private const val ON_SAVE_BACKUP_FILE = "NotallyX_AutoBackup"
private const val PERIODIC_BACKUP_FILE_PREFIX = "NotallyX_Backup_"

fun ContextWrapper.createBackup(): Result {
    val app = applicationContext as Application
    val preferences = NotallyXPreferences.getInstance(app)
    val (_, maxBackups) = preferences.periodicBackups.value
    val path = preferences.backupsFolder.value

    if (path != EMPTY_PATH) {
        val uri = path.toUri()
        val folder =
            requireBackupFolder(
                path,
                "Periodic Backup failed, because auto-backup path '$path' is invalid",
            ) ?: return Result.success()
        try {
            val backupFilePrefix = PERIODIC_BACKUP_FILE_PREFIX
            val name =
                "$backupFilePrefix${BACKUP_TIMESTAMP_FORMATTER.format(System.currentTimeMillis())}"
            log(TAG, msg = "Creating '$uri/$name.zip'...")
            val zipUri = folder.createFileSafe(MIME_TYPE_ZIP, name, ".zip").uri
            val exportedNotes = app.exportAsZip(zipUri, password = preferences.backupPassword.value)
            log(TAG, msg = "Exported $exportedNotes notes")
            val backupFiles = folder.listZipFiles(backupFilePrefix)
            log(TAG, msg = "Found ${backupFiles.size} backups")
            val backupsToBeDeleted = backupFiles.drop(maxBackups)
            if (backupsToBeDeleted.isNotEmpty()) {
                log(
                    TAG,
                    msg =
                        "Deleting ${backupsToBeDeleted.size} oldest backups (maxBackups: $maxBackups): ${backupsToBeDeleted.joinToString { "'${it.name.toString()}'" }}",
                )
            }
            backupsToBeDeleted.forEach {
                if (it.exists()) {
                    it.delete()
                }
            }
            log(TAG, msg = "Finished backup to '$zipUri'")
            preferences.periodicBackupLastExecution.save(Date().time)
            return Result.success(
                Data.Builder().putString(OUTPUT_DATA_BACKUP_URI, zipUri.path!!).build()
            )
        } catch (e: Exception) {
            log(TAG, msg = "Failed creating backup to '$path'", throwable = e)
            tryPostErrorNotification(e)
            return Result.success(
                Data.Builder().putString(OUTPUT_DATA_EXCEPTION, e.message).build()
            )
        }
    }
    return Result.success()
}

fun ContextWrapper.autoBackupOnSaveFileExists(backupPath: String): Boolean {
    val backupFolderFile = DocumentFile.fromTreeUri(this, backupPath.toUri())
    return backupFolderFile?.let {
        val autoBackupFile = it.findFile("$ON_SAVE_BACKUP_FILE.zip")
        autoBackupFile == null || !autoBackupFile.exists()
    } ?: false
}

fun ContextWrapper.autoBackupOnSave(backupPath: String, password: String, savedNote: BaseNote?) {
    val folder =
        requireBackupFolder(
            backupPath,
            "Auto backup on note save (${savedNote?.let { "id: '${savedNote.id}, title: '${savedNote.title}'" }}) failed, because auto-backup path '$backupPath' is invalid",
        ) ?: return
    try {
        var changedNote = savedNote
        var backupFile = folder.findFile("$ON_SAVE_BACKUP_FILE.zip")
        backupFile =
            if (backupFile == null || !backupFile.exists()) {
                if (savedNote != null) {
                    log("Re-creating full backup since auto backup ZIP unexpectedly does not exist")
                    changedNote = null
                }
                folder.createFileSafe(MIME_TYPE_ZIP, ON_SAVE_BACKUP_FILE, ".zip")
            } else backupFile
        if (changedNote == null) {
            // Export all notes
            exportAsZip(backupFile.uri, password = password)
        } else {
            // Only add changed note to existing backup ZIP
            val (_, file) = copyDatabase()
            val files =
                with(changedNote) {
                    images.map {
                        BackupFile(
                            SUBFOLDER_IMAGES,
                            File(getExternalImagesDirectory(), it.localName),
                        )
                    } +
                        files.map {
                            BackupFile(
                                SUBFOLDER_FILES,
                                File(getExternalFilesDirectory(), it.localName),
                            )
                        } +
                        audios.map {
                            BackupFile(SUBFOLDER_AUDIOS, File(getExternalAudioDirectory(), it.name))
                        } +
                        BackupFile(null, file)
                }
            try {
                exportToZip(backupFile.uri, files, password)
            } catch (e: ZipException) {
                log(
                    TAG,
                    msg =
                        "Re-creating full backup since existing auto backup ZIP is corrupt: ${e.message}",
                )
                backupFile.delete()
                autoBackupOnSave(backupPath, password, savedNote)
            }
        }
    } catch (e: Exception) {
        try {
            log(
                TAG,
                "Auto backup on note save (${savedNote?.let { "id: '${savedNote.id}, title: '${savedNote.title}'" }}) failed",
                e,
            )
        } catch (logException: Exception) {
            tryPostErrorNotification(logException)
            return
        }
        tryPostErrorNotification(e)
    }
}

private fun ContextWrapper.requireBackupFolder(path: String, msg: String): DocumentFile? {
    return try {
        val folder = DocumentFile.fromTreeUri(this, path.toUri())!!
        if (!folder.exists()) {
            log(TAG, msg = msg)
            tryPostErrorNotification(BackupFolderNotExistsException(path))
            return null
        }
        folder
    } catch (e: Exception) {
        log(TAG, msg = msg, throwable = e)
        tryPostErrorNotification(BackupFolderNotExistsException(path, e))
        return null
    }
}

suspend fun ContextWrapper.checkBackupOnSave(
    preferences: NotallyXPreferences,
    note: BaseNote? = null,
    forceFullBackup: Boolean = false,
) {
    if (preferences.backupOnSave.value) {
        val backupPath = preferences.backupsFolder.value
        if (backupPath != EMPTY_PATH) {
            if (forceFullBackup) {
                deleteModifiedNoteBackup(backupPath)
            }
            withContext(Dispatchers.IO) {
                autoBackupOnSave(backupPath, preferences.backupPassword.value, note)
            }
        }
    }
}

fun ContextWrapper.deleteModifiedNoteBackup(backupPath: String) {
    DocumentFile.fromTreeUri(this, backupPath.toUri())
        ?.findFile("$ON_SAVE_BACKUP_FILE.zip")
        ?.delete()
}

fun ContextWrapper.modifiedNoteBackupExists(backupPath: String): Boolean {
    return DocumentFile.fromTreeUri(this, backupPath.toUri())
        ?.findFile("$ON_SAVE_BACKUP_FILE.zip")
        ?.exists() ?: false
}

fun ContextWrapper.exportAsZip(
    fileUri: Uri,
    compress: Boolean = false,
    password: String = PASSWORD_EMPTY,
    backupProgress: MutableLiveData<Progress>? = null,
): Int {
    backupProgress?.postValue(BackupProgress(indeterminate = true))
    val tempFile = File.createTempFile("export", "tmp", cacheDir)
    try {
        val zipFile =
            ZipFile(tempFile, if (password != PASSWORD_EMPTY) password.toCharArray() else null)
        val zipParameters =
            ZipParameters().apply {
                isEncryptFiles = password != PASSWORD_EMPTY
                if (!compress) {
                    compressionLevel = CompressionLevel.NO_COMPRESSION
                }
                encryptionMethod = EncryptionMethod.AES
            }

        val (databaseOriginal, databaseCopy) = copyDatabase()
        zipFile.addFile(databaseCopy, zipParameters.copy(DATABASE_NAME))
        databaseCopy.delete()

        val imageRoot = getExternalImagesDirectory()
        val fileRoot = getExternalFilesDirectory()
        val audioRoot = getExternalAudioDirectory()

        val totalNotes = databaseOriginal.getBaseNoteDao().count()
        val images = databaseOriginal.getBaseNoteDao().getAllImages().toFileAttachments()
        val files = databaseOriginal.getBaseNoteDao().getAllFiles().toFileAttachments()
        val audios = databaseOriginal.getBaseNoteDao().getAllAudios()
        val totalAttachments = images.count() + files.count() + audios.size
        backupProgress?.postValue(BackupProgress(0, totalAttachments))

        val counter = AtomicInteger(0)
        images.export(
            zipFile,
            zipParameters,
            imageRoot,
            SUBFOLDER_IMAGES,
            this,
            backupProgress,
            totalAttachments,
            counter,
        )
        files.export(
            zipFile,
            zipParameters,
            fileRoot,
            SUBFOLDER_FILES,
            this,
            backupProgress,
            totalAttachments,
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
                    backupProgress?.postValue(
                        BackupProgress(counter.incrementAndGet(), totalAttachments)
                    )
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
        backupProgress?.postValue(BackupProgress(inProgress = false))
        return totalNotes
    } finally {
        tempFile.delete()
    }
}

fun Context.exportToZip(
    zipUri: Uri,
    files: List<BackupFile>,
    password: String = PASSWORD_EMPTY,
): Boolean {
    val tempDir = File(cacheDir, "export").recreateDir()
    try {
        val zipInputStream = contentResolver.openInputStream(zipUri) ?: return false
        extractZipToDirectory(zipInputStream, tempDir, password)
        files.forEach { file ->
            val targetFile = File(tempDir, "${file.first?.let { "$it/" } ?: ""}${file.second.name}")
            file.second.copyTo(targetFile, overwrite = true)
        }
        val zipOutputStream = contentResolver.openOutputStream(zipUri, "w") ?: return false
        createZipFromDirectory(tempDir, zipOutputStream, password)
    } finally {
        tempDir.deleteRecursively()
    }
    return true
}

private fun extractZipToDirectory(zipInputStream: InputStream, outputDir: File, password: String) {
    val tempZipFile = File.createTempFile("extractedZip", null, outputDir)
    try {
        tempZipFile.outputStream().use { zipOutputStream -> zipInputStream.copyTo(zipOutputStream) }
        val zipFile =
            ZipFile(tempZipFile, if (password != PASSWORD_EMPTY) password.toCharArray() else null)
        zipFile.extractAll(outputDir.absolutePath)
    } finally {
        tempZipFile.delete()
    }
}

private fun createZipFromDirectory(
    sourceDir: File,
    zipOutputStream: OutputStream,
    password: String = PASSWORD_EMPTY,
    compress: Boolean = false,
) {
    val tempZipFile = File.createTempFile("tempZip", ".zip")
    try {
        tempZipFile.deleteOnExit()
        val zipFile =
            ZipFile(tempZipFile, if (password != PASSWORD_EMPTY) password.toCharArray() else null)
        val zipParameters =
            ZipParameters().apply {
                isEncryptFiles = password != PASSWORD_EMPTY
                if (!compress) {
                    compressionLevel = CompressionLevel.NO_COMPRESSION
                }
                encryptionMethod = EncryptionMethod.AES
                isIncludeRootFolder = false
            }
        zipFile.addFolder(sourceDir, zipParameters)
        tempZipFile.inputStream().use { inputStream -> inputStream.copyTo(zipOutputStream) }
    } finally {
        tempZipFile.delete()
    }
}

fun ContextWrapper.copyDatabase(): Pair<NotallyDatabase, File> {
    val database = NotallyDatabase.getDatabase(this, observePreferences = false).value
    database.checkpoint()
    val preferences = NotallyXPreferences.getInstance(this)
    val databaseFile = NotallyDatabase.getCurrentDatabaseFile(this)
    return if (preferences.isLockEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val cipher = getInitializedCipherForDecryption(iv = preferences.iv.value!!)
        val passphrase = cipher.doFinal(preferences.databaseEncryptionKey.value)
        val decryptedFile = File(cacheDir, DATABASE_NAME)
        decryptDatabase(this, passphrase, databaseFile, decryptedFile)
        Pair(database, decryptedFile)
    } else {
        val dbFile = File(cacheDir, DATABASE_NAME)
        databaseFile.copyTo(dbFile, overwrite = true)
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
            backupProgress?.postValue(BackupProgress(counter.incrementAndGet(), total))
        }
    }
}

fun WorkInfo.PeriodicityInfo.isEqualTo(value: Long, unit: TimeUnit): Boolean {
    return repeatIntervalMillis == unit.toMillis(value)
}

fun List<WorkInfo>.containsNonCancelled(): Boolean = any { it.state != WorkInfo.State.CANCELLED }

fun WorkManager.cancelAutoBackup() {
    Log.d(TAG, "Cancelling auto backup work")
    cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
}

fun WorkManager.updateAutoBackup(workInfos: List<WorkInfo>, autoBackPeriodInDays: Long) {
    Log.d(TAG, "Updating auto backup schedule for period: $autoBackPeriodInDays days")
    val workInfoId = workInfos.first().id
    val updatedWorkRequest =
        PeriodicWorkRequest.Builder(
                AutoBackupWorker::class.java,
                autoBackPeriodInDays.toLong(),
                TimeUnit.DAYS,
            )
            .setId(workInfoId)
            .build()
    updateWork(updatedWorkRequest)
}

fun WorkManager.scheduleAutoBackup(context: ContextWrapper, periodInDays: Long) {
    Log.d(TAG, "Scheduling auto backup for period: $periodInDays days")
    val request =
        PeriodicWorkRequest.Builder(AutoBackupWorker::class.java, periodInDays, TimeUnit.DAYS)
            .build()
    try {
        enqueueUniquePeriodicWork(AUTO_BACKUP_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    } catch (e: IllegalStateException) {
        // only happens in Unit-Tests
        context.log(TAG, "Scheduling auto backup failed", throwable = e)
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
    app: ContextWrapper,
    note: BaseNote,
    folder: DocumentFile,
    fileName: String = note.title,
    pdfPrintListener: PdfPrintListener? = null,
    progress: MutableLiveData<Progress>? = null,
    counter: AtomicInteger? = null,
    total: Int? = null,
    duplicateFileCount: Int = 1,
) {
    val validFileName = fileName.ifBlank { app.getString(R.string.note) }
    val filePath = "$validFileName.${ExportMimeType.PDF.fileExtension}"
    if (folder.findFile(filePath)?.exists() == true) {
        val duplicateFileName =
            findFreeDuplicateFileName(folder, validFileName, ExportMimeType.PDF.fileExtension)
        return exportPdfFile(
            app,
            note,
            folder,
            duplicateFileName,
            pdfPrintListener,
            progress,
            counter,
            total,
            duplicateFileCount + 1,
        )
    }
    folder
        .createFileSafe(
            ExportMimeType.PDF.mimeType,
            validFileName,
            ".${ExportMimeType.PDF.fileExtension}",
        )
        .let {
            val file = DocumentFile.fromFile(File(app.getExportedPath(), filePath))
            val html =
                note.toHtml(
                    NotallyXPreferences.getInstance(app).showDateCreated(),
                    app.getExternalImagesDirectory(),
                )
            app.printPdf(
                file,
                html,
                object : PdfPrintListener {
                    override fun onSuccess(file: DocumentFile) {
                        app.contentResolver.openOutputStream(it.uri)?.use { outStream ->
                            app.contentResolver.openInputStream(file.uri)?.copyTo(outStream)
                        }
                        if (progress != null) {
                            progress.postValue(
                                BackupProgress(
                                    current = counter!!.incrementAndGet(),
                                    total = total!!,
                                )
                            )
                            if (counter.get() == total) {
                                pdfPrintListener?.onSuccess(file)
                            }
                        } else {
                            pdfPrintListener?.onSuccess(file)
                        }
                    }

                    override fun onFailure(message: CharSequence?) {
                        pdfPrintListener?.onFailure(message)
                    }
                },
            )
        }
}

suspend fun exportPlainTextFile(
    app: ContextWrapper,
    note: BaseNote,
    exportType: ExportMimeType,
    folder: DocumentFile,
    fileName: String = note.title,
    progress: MutableLiveData<Progress>? = null,
    counter: AtomicInteger? = null,
    total: Int? = null,
    duplicateFileCount: Int = 1,
): DocumentFile? {
    val validFileName = fileName.ifBlank { app.getString(R.string.note) }
    if (folder.findFile("$validFileName.${exportType.fileExtension}")?.exists() == true) {
        val duplicateFileName =
            findFreeDuplicateFileName(folder, validFileName, exportType.fileExtension)
        return exportPlainTextFile(
            app,
            note,
            exportType,
            folder,
            duplicateFileName,
            progress,
            counter,
            total,
            duplicateFileCount + 1,
        )
    }
    return withContext(Dispatchers.IO) {
        val file =
            folder
                .createFileSafe(exportType.mimeType, validFileName, ".${exportType.fileExtension}")
                .let {
                    app.contentResolver.openOutputStream(it.uri)?.use { stream ->
                        OutputStreamWriter(stream).use { writer ->
                            writer.write(
                                when (exportType) {
                                    ExportMimeType.TXT ->
                                        note.toTxt(
                                            includeTitle = false,
                                            includeCreationDate = false,
                                        )

                                    ExportMimeType.JSON -> note.toJson()
                                    ExportMimeType.HTML ->
                                        note.toHtml(
                                            NotallyXPreferences.getInstance(app).showDateCreated(),
                                            app.getExternalImagesDirectory(),
                                        )

                                    else -> TODO("Unsupported MimeType for Export")
                                }
                            )
                        }
                    }
                    it
                }
        progress?.postValue(BackupProgress(current = counter!!.incrementAndGet(), total = total!!))
        return@withContext file
    }
}

private fun findFreeDuplicateFileName(
    folder: DocumentFile,
    fileName: String,
    fileExtension: String,
): String {
    val existingNames = folder.listFiles().mapNotNull { it.name }.toSet()
    if ("$fileName.$fileExtension" !in existingNames) return fileName

    var index = 0
    var newName: String

    do {
        index++
        newName = "$fileName ($index).$fileExtension"
    } while (newName in existingNames)

    return "$fileName ($index)"
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

private fun ContextWrapper.tryPostErrorNotification(e: Throwable) {
    fun postErrorNotification(e: Throwable) {
        getSystemService<NotificationManager>()?.let { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createChannelIfNotExists(NOTIFICATION_CHANNEL_ID)
            }
            val bugIntent =
                try {
                    createReportBugIntent(
                        e.stackTraceToString(),
                        title = "Auto Backup failed",
                        body = "Error occurred during auto backup, see logs below",
                    )
                } catch (e: IllegalArgumentException) {
                    createReportBugIntent(
                        stackTrace =
                            "PLEASE PASTE YOUR NOTALLYX LOGS FILE CONTENT HERE (Error Notification -> 'View Logs')",
                        title = "Auto Backup failed",
                        body = "Error occurred during auto backup, see logs below.",
                    )
                }
            val notificationBuilder =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.error)
                    .setContentTitle(getString(R.string.auto_backup_failed))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(
                                getString(
                                    R.string.auto_backup_error_message,
                                    "${e.javaClass.simpleName}: ${e.localizedMessage}",
                                )
                            )
                    )
                    .addAction(
                        R.drawable.settings,
                        getString(R.string.settings),
                        PendingIntent.getActivity(
                            this,
                            0,
                            Intent(this, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_FRAGMENT_TO_OPEN, R.id.Settings)
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    .addAction(
                        R.drawable.error,
                        getString(R.string.report_bug),
                        PendingIntent.getActivity(
                            this,
                            0,
                            bugIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    .addAction(
                        R.drawable.text_file,
                        getString(R.string.view_logs),
                        PendingIntent.getActivity(
                            this,
                            0,
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(getLogFileUri(), "text/plain")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )

            // Add a "Select Folder" button if the error is about a missing folder
            if (e is BackupFolderNotExistsException) {
                notificationBuilder.addAction(
                    R.drawable.settings,
                    getString(R.string.choose_folder),
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.EXTRA_FRAGMENT_TO_OPEN, R.id.Settings)
                            putExtra(SettingsFragment.EXTRA_SHOW_IMPORT_BACKUPS_FOLDER, true)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }

            manager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            postErrorNotification(e)
        }
    } else {
        postErrorNotification(e)
    }
}

fun LockedActivity<*>.exportNotes(
    mimeType: ExportMimeType,
    notes: Collection<BaseNote>,
    saveFileResultLauncher: ActivityResultLauncher<Intent>,
    exportToFolderResultLauncher: ActivityResultLauncher<Intent>,
) {
    baseModel.selectedExportMimeType = mimeType
    if (notes.size == 1) {
        val baseNote = notes.first()
        when (mimeType) {
            ExportMimeType.PDF -> {
                exportPdfFile(
                    this,
                    baseNote,
                    DocumentFile.fromFile(getExportedPath()),
                    pdfPrintListener =
                        object : PdfPrintListener {

                            override fun onSuccess(file: DocumentFile) {
                                showFileOptionsDialog(
                                    file,
                                    ExportMimeType.PDF.mimeType,
                                    saveFileResultLauncher,
                                )
                            }

                            override fun onFailure(message: CharSequence?) {
                                Toast.makeText(
                                        this@exportNotes,
                                        R.string.something_went_wrong,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        },
                )
            }
            else ->
                lifecycleScope.launch {
                    exportPlainTextFile(
                            this@exportNotes,
                            baseNote,
                            mimeType,
                            DocumentFile.fromFile(getExportedPath()),
                        )
                        ?.let {
                            showFileOptionsDialog(it, mimeType.mimeType, saveFileResultLauncher)
                        }
                }
        }
    } else {
        lifecycleScope.launch {
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .apply { addCategory(Intent.CATEGORY_DEFAULT) }
                    .wrapWithChooser(this@exportNotes)
            exportToFolderResultLauncher.launch(intent)
        }
    }
}

private fun LockedActivity<*>.showFileOptionsDialog(
    file: DocumentFile,
    mimeType: String,
    resultLauncher: ActivityResultLauncher<Intent>,
) {
    MenuDialog(this)
        .add(R.string.view_file) { viewFile(getUriForFile(File(file.uri.path!!)), mimeType) }
        .add(R.string.save_to_device) { saveFileToDevice(file, mimeType, resultLauncher) }
        .show()
}

private fun LockedActivity<*>.saveFileToDevice(
    file: DocumentFile,
    mimeType: String,
    resultLauncher: ActivityResultLauncher<Intent>,
) {
    val intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .apply {
                type = mimeType
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, file.nameWithoutExtension!!)
            }
            .wrapWithChooser(this)
    baseModel.selectedExportFile = file
    resultLauncher.launch(intent)
}
