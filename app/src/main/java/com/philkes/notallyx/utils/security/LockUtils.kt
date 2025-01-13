package com.philkes.notallyx.utils.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
        this,
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
        requireContext(),
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
    context: Context,
    activityResultLauncher: ActivityResultLauncher<Intent>,
    titleResId: Int,
    descriptionResId: Int? = null,
    cipherIv: ByteArray? = null,
    onSuccess: (cipher: Cipher) -> Unit,
    onFailure: (errorCode: Int?) -> Unit,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            // Android 11+ with BiometricPrompt and Authenticators
            val prompt =
                BiometricPrompt.Builder(context)
                    .apply {
                        setTitle(context.getString(titleResId))
                        descriptionResId?.let {
                            setDescription(context.getString(descriptionResId))
                        }
                        setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                    }
                    .build()
            val cipher =
                if (isForDecrypt) {
                    getInitializedCipherForDecryption(iv = cipherIv!!)
                } else {
                    getInitializedCipherForEncryption()
                }
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                getCancellationSignal(context),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess.invoke(result!!.cryptoObject!!.cipher)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure.invoke(null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailure.invoke(errorCode)
                    }
                },
            )
        }
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
            // Android 10: Use BiometricPrompt without Authenticators
            val prompt =
                BiometricPrompt.Builder(context)
                    .apply {
                        setTitle(context.getString(titleResId))
                        descriptionResId?.let {
                            setDescription(context.getString(descriptionResId))
                        }
                        setNegativeButton(
                            context.getString(R.string.cancel),
                            context.mainExecutor,
                        ) { _, _ ->
                            onFailure.invoke(null)
                        }
                    }
                    .build()
            val cipher =
                if (isForDecrypt) {
                    getInitializedCipherForDecryption(iv = cipherIv!!)
                } else {
                    getInitializedCipherForEncryption()
                }
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                getCancellationSignal(context),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess.invoke(result!!.cryptoObject!!.cipher)
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure.invoke(null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailure.invoke(errorCode)
                    }
                },
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            val fingerprintManager =
                ContextCompat.getSystemService(context, FingerprintManager::class.java)
            if (
                fingerprintManager?.isHardwareDetected == true &&
                    fingerprintManager.hasEnrolledFingerprints()
            ) {
                val cipher =
                    if (isForDecrypt) {
                        getInitializedCipherForDecryption(iv = cipherIv!!)
                    } else {
                        getInitializedCipherForEncryption()
                    }
                fingerprintManager.authenticate(
                    FingerprintManager.CryptoObject(cipher),
                    getCancellationSignal(context),
                    0,
                    object : FingerprintManager.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: FingerprintManager.AuthenticationResult?
                        ) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess.invoke(result!!.cryptoObject!!.cipher)
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onFailure.invoke(null)
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence?,
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            onFailure.invoke(errorCode)
                        }
                    },
                    null,
                )
            } else {
                promptPinAuthentication(context, activityResultLauncher, titleResId, onFailure)
            }
        }

        else -> {
            // API 21-22: No biometric support, fallback to PIN/Password
            promptPinAuthentication(context, activityResultLauncher, titleResId, onFailure)
        }
    }
}

private fun getCancellationSignal(context: Context): CancellationSignal {
    return CancellationSignal().apply {
        setOnCancelListener {
            Toast.makeText(context, R.string.biometrics_failure, Toast.LENGTH_SHORT).show()
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
