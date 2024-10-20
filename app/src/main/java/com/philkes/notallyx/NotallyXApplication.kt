package com.philkes.notallyx

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.presentation.view.misc.Theme
import com.philkes.notallyx.utils.backup.scheduleAutoBackup
import com.philkes.notallyx.utils.security.UnlockReceiver

class NotallyXApplication : Application() {

    private lateinit var biometricLockObserver: Observer<String>
    private lateinit var preferences: Preferences
    private var unlockReceiver: UnlockReceiver? = null

    var isLocked = true

    override fun onCreate() {
        super.onCreate()

        preferences = Preferences.getInstance(this)
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

        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF) }
        biometricLockObserver = Observer {
            if (it == enabled) {
                unlockReceiver = UnlockReceiver(this)
                registerReceiver(unlockReceiver, filter)
            } else if (unlockReceiver != null) {
                unregisterReceiver(unlockReceiver)
            }
        }
        preferences.biometricLock.observeForever(biometricLockObserver)
    }
}
