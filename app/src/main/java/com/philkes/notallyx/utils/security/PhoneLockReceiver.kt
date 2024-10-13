package com.philkes.notallyx.utils.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.philkes.notallyx.NotallyXApplication

class UnlockReceiver(private val application: NotallyXApplication) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            application.needUnlock = true
        }
    }
}
