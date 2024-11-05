package com.philkes.notallyx.presentation.activity

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt

abstract class LockedActivity<T : ViewBinding> : AppCompatActivity() {

    private lateinit var notallyXApplication: NotallyXApplication
    private lateinit var biometricAuthenticationActivityResultLauncher:
        ActivityResultLauncher<Intent>

    protected lateinit var binding: T
    protected lateinit var preferences: NotallyXPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notallyXApplication = (application as NotallyXApplication)
        preferences = NotallyXPreferences.getInstance(application)

        biometricAuthenticationActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    unlock()
                } else {
                    finish()
                }
            }
    }

    override fun onResume() {
        if (preferences.biometricLock.value == BiometricLock.ENABLED) {
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
        if (preferences.biometricLock.value == BiometricLock.ENABLED) {
            hide()
        }
    }

    open fun showLockScreen() {
        showBiometricOrPinPrompt(
            true,
            preferences.iv.value!!,
            biometricAuthenticationActivityResultLauncher,
            R.string.unlock,
            onSuccess = { unlock() },
        ) {
            finish()
        }
    }

    private fun unlock() {
        notallyXApplication.locked.value = false
        show()
    }

    protected fun show() {
        binding.root.visibility = VISIBLE
    }

    protected fun hide() {
        binding.root.visibility = INVISIBLE
    }

    private fun hasToAuthenticateWithBiometric(): Boolean {
        return ContextCompat.getSystemService(this, KeyguardManager::class.java)?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                (it.isDeviceLocked || notallyXApplication.locked.value)
            } else {
                false
            }
        } ?: false
    }
}
