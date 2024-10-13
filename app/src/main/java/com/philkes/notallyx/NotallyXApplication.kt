package com.philkes.notallyx

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.philkes.notallyx.presentation.view.misc.Theme
import com.philkes.notallyx.utils.backup.scheduleAutoBackup

class NotallyXApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val preferences = Preferences.getInstance(this)
        preferences.theme.observeForever { theme ->
            when (theme) {
                Theme.dark ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Theme.light ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Theme.followSystem ->
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
            }
        }
        scheduleAutoBackup(preferences.autoBackupPeriodDays.value.toLong(), this)
    }
}
