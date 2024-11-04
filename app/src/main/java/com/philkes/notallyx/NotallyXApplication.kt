package com.philkes.notallyx

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF) }
        biometricLockObserver = Observer { biometricLock ->
            if (biometricLock == BiometricLock.ENABLED) {
                unlockReceiver = UnlockReceiver(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(unlockReceiver, filter)
                }
            } else {
                unlockReceiver?.let { unregisterReceiver(it) }
                if (locked.value) {
                    locked.postValue(false)
                }
            }
        }
        preferences.biometricLock.observeForever(biometricLockObserver)

        locked.observeForever { isLocked -> WidgetProvider.updateWidgets(this, locked = isLocked) }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
}
