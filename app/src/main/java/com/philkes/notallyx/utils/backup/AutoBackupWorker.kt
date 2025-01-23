package com.philkes.notallyx.utils.backup

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AutoBackupWorker(private val context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        return context.createBackup()
    }
}
