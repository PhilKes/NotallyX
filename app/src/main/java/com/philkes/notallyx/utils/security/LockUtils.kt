package com.philkes.notallyx.utils.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.philkes.notallyx.R
import javax.crypto.Cipher

fun Activity.showBiometricOrPinPrompt(
    isForDecrypt: Boolean,
    cipherIv: ByteArray? = null,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    titleResId: Int,
    descriptionResId: Int? = null,
    onSuccess: (cipher: Cipher) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
) {
    showBiometricOrPinPrompt(
        isForDecrypt,
        this as FragmentActivity,
        activityResultLauncher,
        titleResId,
        descriptionResId,
        cipherIv,
        onSuccess,
        onFailure,
    )
}

fun Fragment.showBiometricOrPinPrompt(
    isForDecrypt: Boolean,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    titleResId: Int,
    descriptionResId: Int,
    cipherIv: ByteArray? = null,
    onSuccess: (cipher: Cipher) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
) {
    showBiometricOrPinPrompt(
        isForDecrypt,
        activity!!,
        activityResultLauncher,
        titleResId,
        descriptionResId,
        cipherIv,
        onSuccess,
        onFailure,
    )
}

private fun showBiometricOrPinPrompt(
    isForDecrypt: Boolean,
    context: FragmentActivity,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    titleResId: Int,
    descriptionResId: Int? = null,
    cipherIv: ByteArray? = null,
    onSuccess: (cipher: Cipher) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            val promptInfo =
                BiometricPrompt.PromptInfo.Builder()
                    .apply {
                        setTitle(context.getString(titleResId))
                        descriptionResId?.let {
                            setDescription(context.getString(descriptionResId))
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            )
                        } else {
                            setNegativeButtonText(context.getString(R.string.cancel))
                            setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                            )
                        }
                    }
                    .build()
            val cipher =
                if (isForDecrypt) {
                    getInitializedCipherForDecryption(iv = cipherIv!!)
                } else {
                    getInitializedCipherForEncryption()
                }
            val authCallback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess.invoke(result.cryptoObject!!.cipher!!)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure.invoke(null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailure.invoke(errorCode)
                    }
                }
            val prompt =
                BiometricPrompt(context, ContextCompat.getMainExecutor(context), authCallback)
            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }

        else -> {
            // API 21-22: No biometric support, fallback to PIN/Password
            promptPinAuthentication(context, activityResultLauncher, titleResId, onFailure)
        }
    }
}

private fun promptPinAuthentication(
    context: Context,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    titleResId: Int,
    onFailure: (errorCode: Int?) -> Unit,
) {
    val keyguardManager = ContextCompat.getSystemService(context, KeyguardManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // For API 23 and above, use isDeviceSecure
        if (keyguardManager?.isDeviceSecure == true) {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    context.getString(titleResId),
                    null,
                )
            if (intent != null) {
                activityResultLauncher.launch(intent)
            } else {
                onFailure.invoke(null)
            }
        } else {
            onFailure.invoke(null)
        }
    } else {
        // For API 21-22, use isKeyguardSecure
        if (keyguardManager?.isKeyguardSecure == true) {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    context.getString(titleResId),
                    null,
                )
            if (intent != null) {
                activityResultLauncher.launch(intent)
            } else {
                onFailure.invoke(null)
            }
        } else {
            onFailure.invoke(null)
        }
    }
}
