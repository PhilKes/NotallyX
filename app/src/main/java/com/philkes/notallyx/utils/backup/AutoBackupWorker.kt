package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.utils.logToFile
import java.text.SimpleDateFormat
import java.util.Locale

class AutoBackupWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val app = context.applicationContext as Application
        val preferences = NotallyXPreferences.getInstance(app)
        val (path, _, maxBackups) = preferences.autoBackup.value

        if (path != EMPTY_PATH) {
            val uri = Uri.parse(path)
            val folder = requireNotNull(DocumentFile.fromTreeUri(app, uri))
            fun log(msg: String? = null, throwable: Throwable? = null, stackTrace: String? = null) {
                context.logToFile(
                    TAG,
                    folder,
                    "notallyx-backup-logs.txt",
                    msg = msg,
                    throwable = throwable,
                    stackTrace = stackTrace,
                )
            }

            if (folder.exists()) {
                val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
                val backupFilePrefix = "NotallyX_Backup_"
                val name = "$backupFilePrefix${formatter.format(System.currentTimeMillis())}"
                log(msg = "Creating '$uri/$name.zip'...")
                try {
                    val zipUri = requireNotNull(folder.createFile("application/zip", name)).uri
                    app.exportAsZip(zipUri)
                    val backupFiles = folder.listZipFiles(backupFilePrefix)
                    log(msg = "Found ${backupFiles.size} backups")
                    val backupsToBeDeleted = backupFiles.drop(maxBackups)
                    if (backupsToBeDeleted.isNotEmpty()) {
                        log(
                            msg =
                                "Deleting ${backupsToBeDeleted.size} oldest backups (maxBackups: $maxBackups): ${backupsToBeDeleted.joinToString { "'${it.name.toString()}'" }}"
                        )
                    }
                    backupsToBeDeleted.forEach {
                        if (it.exists()) {
                            it.delete()
                        }
                    }

                    log(msg = "Finished backup to '$zipUri'")
                    return Result.success(
                        Data.Builder().putString("backupUri", zipUri.path!!).build()
                    )
                } catch (e: Exception) {
                    log(msg = "Failed creating backup to '$uri/$name'", throwable = e)
                    return Result.success(Data.Builder().putString("exception", e.message).build())
                }
            } else {
                log(msg = "Folder '${folder.uri}' does not exist, therefore skipping auto-backup")
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }
}

fun DocumentFile.listZipFiles(prefix: String): List<DocumentFile> {
    if (!this.isDirectory) return emptyList()
    val zipFiles =
        this.listFiles().filter { file ->
            file.isFile &&
                file.name?.endsWith(".zip", ignoreCase = true) == true &&
                file.name?.startsWith(prefix, ignoreCase = true) == true
        }
    return zipFiles.sortedByDescending { it.lastModified() }
}
