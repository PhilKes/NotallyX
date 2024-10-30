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
    onFailure: () -> Unit,
) {
    showBiometricOrPinPrompt(
        isForDecrypt,
        this,
        activityResultLauncher,
        titleResId,
        descriptionResId,
        cipherIv,
        onSuccess,
        ::startActivityForResult,
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
    onFailure: () -> Unit,
) {
    showBiometricOrPinPrompt(
        isForDecrypt,
        requireContext(),
        activityResultLauncher,
        titleResId,
        descriptionResId,
        cipherIv,
        onSuccess,
        ::startActivityForResult,
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
    startActivityForResult: (intent: Intent, requestCode: Int) -> Unit,
    onFailure: () -> Unit,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            val prompt =
                BiometricPrompt.Builder(context)
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
                        onFailure.invoke()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailure.invoke()
                    }
                },
            )
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            val fingerprintManager =
                context.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
            if (
                fingerprintManager.isHardwareDetected &&
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
                            onFailure.invoke()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence?,
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            onFailure.invoke()
                        }
                    },
                    null,
                )
            } else {
                promptPinAuthentication(
                    context,
                    activityResultLauncher,
                    titleResId,
                    startActivityForResult,
                    onFailure,
                )
            }
        }

        else -> {
            // API 21-22: No biometric support, fallback to PIN/Password
            promptPinAuthentication(
                context,
                activityResultLauncher,
                titleResId,
                startActivityForResult,
                onFailure,
            )
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
    startActivityForResult: (intent: Intent, requestCode: Int) -> Unit,
    onFailure: () -> Unit,
) {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // For API 23 and above, use isDeviceSecure
        if (keyguardManager.isDeviceSecure) {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    context.getString(titleResId),
                    null,
                )
            if (intent != null) {
                activityResultLauncher.launch(intent)
            } else {
                onFailure.invoke()
            }
        } else {
            onFailure.invoke()
        }
    } else {
        // For API 21-22, use isKeyguardSecure
        if (keyguardManager.isKeyguardSecure) {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    context.getString(titleResId),
                    null,
                )
            if (intent != null) {
                activityResultLauncher.launch(intent)
            } else {
                onFailure.invoke()
            }
        } else {
            onFailure.invoke()
        }
    }
}
