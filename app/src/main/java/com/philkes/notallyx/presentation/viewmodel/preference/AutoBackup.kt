package com.philkes.notallyx.presentation.viewmodel.preference

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH

class AutoBackupPreference(sharedPreferences: SharedPreferences) :
    BasePreference<AutoBackup>(sharedPreferences, AutoBackup()) {

    override fun getValue(sharedPreferences: SharedPreferences): AutoBackup {
        val path = sharedPreferences.getString(BACKUP_PATH_KEY, defaultValue.path)!!
        val periodInDays =
            sharedPreferences.getInt(BACKUP_PERIOD_DAYS_KEY, defaultValue.periodInDays)
        val maxBackups = sharedPreferences.getInt(BACKUP_MAX_KEY, defaultValue.periodInDays)
        return AutoBackup(path, periodInDays, maxBackups)
    }

    override fun Editor.put(value: AutoBackup) {
        putString(BACKUP_PATH_KEY, value.path)
        putInt(BACKUP_PERIOD_DAYS_KEY, value.periodInDays)
        putInt(BACKUP_MAX_KEY, value.maxBackups)
    }

    companion object {
        const val BACKUP_PATH_KEY = "autoBackup"
        const val BACKUP_PERIOD_DAYS_KEY = "autoBackupPeriodDays"
        const val BACKUP_MAX_KEY = "maxBackups"
    }
}

data class AutoBackup(
    val path: String = EMPTY_PATH,
    val periodInDays: Int = BACKUP_PERIOD_DAYS_MIN,
    val maxBackups: Int = 3,
) {
    companion object {

        const val BACKUP_PERIOD_DAYS_MIN = 1
        const val BACKUP_PERIOD_DAYS_MAX = 31

        const val BACKUP_MAX_MIN = 1
        const val BACKUP_MAX_MAX = 10
    }
}
