package com.philkes.notallyx.presentation.activity

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt

abstract class LockedActivity<T : ViewBinding> : AppCompatActivity() {

    private lateinit var notallyXApplication: NotallyXApplication

    protected lateinit var binding: T
    protected lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notallyXApplication = (application as NotallyXApplication)
        preferences = Preferences.getInstance(application)
    }

    override fun onResume() {
        if (preferences.biometricLock.value == enabled) {
            if (hasToAuthenticateWithBiometric()) {
                hide()
                showLockScreen()
            } else {
                show()
            }
        }
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (preferences.biometricLock.value == enabled) {
            hide()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIOMETRIC_AUTHENTICATION && resultCode == Activity.RESULT_OK) {
            notallyXApplication.needUnlock = false
            show()
        }
    }

    open fun showLockScreen() {
        showBiometricOrPinPrompt(
            REQUEST_BIOMETRIC_AUTHENTICATION,
            R.string.unlock,
            {
                notallyXApplication.needUnlock = false
                show()
            },
        ) {
            finish()
        }
    }

    protected fun show() {
        binding.root.visibility = VISIBLE
    }

    protected fun hide() {
        binding.root.visibility = INVISIBLE
    }

    private fun hasToAuthenticateWithBiometric(): Boolean {
        val keyguardManager: KeyguardManager =
            this.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            (keyguardManager.isDeviceLocked || notallyXApplication.needUnlock)
        } else {
            false
        }
    }

    companion object {
        private const val REQUEST_BIOMETRIC_AUTHENTICATION = 11
    }
}
