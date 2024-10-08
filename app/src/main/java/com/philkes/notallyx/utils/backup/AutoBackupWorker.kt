package com.philkes.notallyx.utils.backup

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.presentation.view.misc.AutoBackup
import java.text.SimpleDateFormat
import java.util.Locale

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
                val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH)
                val name = "NotallyX_Backup_${formatter.format(System.currentTimeMillis())}"
                val zipFile = requireNotNull(folder.createFile("application/zip", name))
                val outputStream = requireNotNull(app.contentResolver.openOutputStream(zipFile.uri))
                doBackup(outputStream, app)
            }
        }

        return Result.success()
    }
}
