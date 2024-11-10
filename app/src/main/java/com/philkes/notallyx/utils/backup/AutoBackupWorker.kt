package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.backup.Export.exportAsZip
import java.text.SimpleDateFormat
import java.util.Locale

class AutoBackupWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        Log.d(TAG, "AutoBackupWorker: Start")
        val app = context.applicationContext as Application
        val preferences = NotallyXPreferences.getInstance(app)
        val (path, _, maxBackups) = preferences.autoBackup.value

        if (path != EMPTY_PATH) {
            val uri = Uri.parse(path)
            val folder = requireNotNull(DocumentFile.fromTreeUri(app, uri))

            if (folder.exists()) {
                val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
                val backupFilePrefix = "NotallyX_Backup_"
                val name = "$backupFilePrefix${formatter.format(System.currentTimeMillis())}"
                try {
                    val zipUri = requireNotNull(folder.createFile("application/zip", name)).uri
                    exportAsZip(zipUri, app)
                    val backupFiles = folder.listZipFiles(backupFilePrefix)
                    backupFiles.drop(maxBackups).forEach {
                        if (it.exists()) {
                            it.delete()
                        }
                    }

                    Log.d(TAG, "AutoBackupWorker: Success")
                    return Result.success(
                        Data.Builder().putString("backupUri", zipUri.path!!).build()
                    )
                } catch (e: Exception) {
                    Operations.log(app, e)
                    return Result.success(Data.Builder().putString("exception", e.message).build())
                }
            }
        }
        return Result.success()
    }

    private fun DocumentFile.listZipFiles(prefix: String): List<DocumentFile> {
        if (!this.isDirectory) return emptyList()
        val zipFiles =
            this.listFiles().filter { file ->
                file.isFile &&
                    file.name?.endsWith(".zip", ignoreCase = true) == true &&
                    file.name?.startsWith(prefix, ignoreCase = true) == true
            }
        return zipFiles.sortedByDescending { it.lastModified() }
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }
}
