package com.philkes.notallyx.utils.backup

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.utils.createChannelIfNotExists
import com.philkes.notallyx.utils.createReportBugIntent
import com.philkes.notallyx.utils.logToFile
import java.text.SimpleDateFormat
import java.util.Locale

class AutoBackupWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        return context.createBackup(TAG)
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
    }
}

fun Context.createBackup(tag: String): Result {
    val app = applicationContext as Application
    val preferences = NotallyXPreferences.getInstance(app)
    val (path, _, maxBackups) = preferences.autoBackup.value

    if (path != EMPTY_PATH) {
        val uri = Uri.parse(path)
        val folder = requireNotNull(DocumentFile.fromTreeUri(app, uri))
        fun log(msg: String? = null, throwable: Throwable? = null, stackTrace: String? = null) {
            logToFile(
                tag,
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
                val exportedNotes = app.exportAsZip(zipUri)
                log(msg = "Exported $exportedNotes notes")
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
                throw IllegalArgumentException("THIS IS BULLSHIT") // TODO
                log(msg = "Finished backup to '$zipUri'")
                return Result.success(Data.Builder().putString("backupUri", zipUri.path!!).build())
            } catch (e: Exception) {
                log(msg = "Failed creating backup to '$uri/$name'", throwable = e)
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
                return Result.success(Data.Builder().putString("exception", e.message).build())
            }
        } else {
            log(msg = "Folder '${folder.uri}' does not exist, therefore skipping auto-backup")
        }
    }
    return Result.success()
}

private fun Context.postErrorNotification(e: Throwable) {
    getSystemService<NotificationManager>()?.let { manager ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createChannelIfNotExists(NOTIFICATION_CHANNEL_ID)
        }
        val notification =
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
                        createReportBugIntent(
                            e.stackTraceToString(),
                            title = "Auto Backup failed",
                            body = "Error occurred during auto backup, see logs below",
                        ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

private const val NOTIFICATION_CHANNEL_ID = "AutoBackups"
private const val NOTIFICATION_ID = 123412

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
