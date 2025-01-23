package com.philkes.notallyx

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.backup.AUTO_BACKUP_WORK_NAME
import com.philkes.notallyx.utils.backup.cancelAutoBackup
import com.philkes.notallyx.utils.backup.containsNonCancelled
import com.philkes.notallyx.utils.backup.isEqualTo
import com.philkes.notallyx.utils.backup.scheduleAutoBackup
import com.philkes.notallyx.utils.backup.updateAutoBackup
import com.philkes.notallyx.utils.observeOnce
import com.philkes.notallyx.utils.security.UnlockReceiver
import java.util.concurrent.TimeUnit

class NotallyXApplication : Application() {

    private lateinit var biometricLockObserver: Observer<BiometricLock>
    private lateinit var preferences: NotallyXPreferences
    private var unlockReceiver: UnlockReceiver? = null

    val locked = NotNullLiveData(true)

    override fun onCreate() {
        super.onCreate()
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

        preferences.autoBackup.observeForeverWithPrevious { (autoBackBefore, autoBackup) ->
            val workManager = WorkManager.getInstance(this)
            workManager.getWorkInfosForUniqueWorkLiveData(AUTO_BACKUP_WORK_NAME).observeOnce {
                workInfos ->
                if (autoBackup.path == EMPTY_PATH) {
                    if (workInfos?.containsNonCancelled() == true) {
                        workManager.cancelAutoBackup()
                    }
                } else if (
                    workInfos.isNullOrEmpty() ||
                        workInfos.all { it.state == WorkInfo.State.CANCELLED } ||
                        (autoBackBefore != null && autoBackBefore.path != autoBackup.path)
                ) {
                    workManager.scheduleAutoBackup(this, autoBackup.periodInDays.toLong())
                } else if (
                    workInfos
                        .first()
                        .periodicityInfo
                        ?.isEqualTo(autoBackup.periodInDays.toLong(), TimeUnit.DAYS) == false
                ) {
                    workManager.updateAutoBackup(workInfos, autoBackup.periodInDays)
                }
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
}
