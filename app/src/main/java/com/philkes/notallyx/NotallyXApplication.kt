package com.philkes.notallyx

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.work.Configuration
import androidx.work.WorkManager
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.viewmodel.preference.AutoBackup
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.backup.Export.cancelAutoBackup
import com.philkes.notallyx.utils.backup.Export.scheduleAutoBackup
import com.philkes.notallyx.utils.security.UnlockReceiver

class NotallyXApplication : Application(), Configuration.Provider {

    private lateinit var biometricLockObserver: Observer<BiometricLock>
    private lateinit var preferences: NotallyXPreferences
    private var unlockReceiver: UnlockReceiver? = null

    val locked = NotNullLiveData(true)

    override fun onCreate() {
        super.onCreate()

        WorkManager.initialize(this, workManagerConfiguration)
        preferences = NotallyXPreferences.getInstance(this)
        preferences.theme.observeForever { theme ->
            when (theme) {
                Theme.DARK ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Theme.LIGHT ->
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Theme.FOLLOW_SYSTEM ->
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    )
            }
        }

        preferences.autoBackup.observeForeverWithPrevious { (prevAutoBackup, autoBackup) ->
            if (autoBackup.path == AutoBackup.BACKUP_PATH_EMPTY) {
                cancelAutoBackup(this)
            } else {
                if (
                    prevAutoBackup != null && prevAutoBackup.periodInDays != autoBackup.periodInDays
                ) {
                    cancelAutoBackup(this)
                }
                scheduleAutoBackup(autoBackup.periodInDays.toLong(), this)
            }
        }

        biometricLockObserver = Observer {
            if (it == BiometricLock.ENABLED) {
                unlockReceiver = UnlockReceiver(this)
                registerReceiver(
                    unlockReceiver,
                    IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF) },
                )
            } else if (unlockReceiver != null) {
                unregisterReceiver(unlockReceiver)
            }
        }
        preferences.biometricLock.observeForever(biometricLockObserver)

        locked.observeForever { isLocked -> WidgetProvider.updateWidgets(this, locked = isLocked) }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
}
