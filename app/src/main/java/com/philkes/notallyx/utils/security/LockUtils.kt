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
import androidx.fragment.app.Fragment
import com.philkes.notallyx.R

fun Activity.showBiometricOrPinPrompt(
    requestCode: Int,
    title: Int,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    showBiometricOrPinPrompt(
        this,
        requestCode,
        title,
        onSuccess,
        ::startActivityForResult,
        onFailure,
    )
}

fun Fragment.showBiometricOrPinPrompt(
    requestCode: Int,
    title: Int,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    showBiometricOrPinPrompt(
        requireContext(),
        requestCode,
        title,
        onSuccess,
        ::startActivityForResult,
        onFailure,
    )
}

private fun showBiometricOrPinPrompt(
    context: Context,
    requestCode: Int,
    title: Int,
    onSuccess: () -> Unit,
    startActivityForResult: (intent: Intent, requestCode: Int) -> Unit,
    onFailure: () -> Unit,
) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
            // API 30 and above: Use BiometricPrompt with setAllowedAuthenticators
            val prompt =
                BiometricPrompt.Builder(context)
                    .setTitle(context.getString(title))
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
            prompt.authenticate(
                getCancellationSignal(context),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess.invoke()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure.invoke()
                    }
                },
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
            // API 28-29: Use BiometricPrompt without setAllowedAuthenticators
            val prompt =
                BiometricPrompt.Builder(context)
                    .setTitle(context.getString(title))
                    .setNegativeButton(context.getString(R.string.cancel), context.mainExecutor) {
                        _,
                        _ ->
                        //                        Toast.makeText(context, "Authentication
                        // cancelled", Toast.LENGTH_SHORT)
                        //                            .show()
                    }
                    .build()
            prompt.authenticate(
                getCancellationSignal(context),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult?
                    ) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess.invoke()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure.invoke()
                    }
                },
            )
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            // API 23-27: Use FingerprintManager
            val fingerprintManager =
                context.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
            if (
                fingerprintManager.isHardwareDetected &&
                    fingerprintManager.hasEnrolledFingerprints()
            ) {
                val cryptoObject: FingerprintManager.CryptoObject? =
                    null // Setup CryptoObject if needed
                fingerprintManager.authenticate(
                    cryptoObject,
                    getCancellationSignal(context),
                    0,
                    object : FingerprintManager.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: FingerprintManager.AuthenticationResult?
                        ) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess.invoke()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onFailure.invoke()
                        }
                    },
                    null,
                )
            } else {
                // No fingerprint available, fallback to PIN
                promptPinAuthentication(
                    context,
                    requestCode,
                    title,
                    startActivityForResult,
                    onFailure,
                )
            }
        }

        else -> {
            // API 21-22: No biometric support, fallback to PIN/Password
            promptPinAuthentication(context, requestCode, title, startActivityForResult, onFailure)
        }
    }
}

// Function to handle the cancellation signal
private fun getCancellationSignal(context: Context): CancellationSignal {
    return CancellationSignal().apply {
        setOnCancelListener {
            //            Toast.makeText(context, "Authentication cancelled",
            // Toast.LENGTH_SHORT).show()
        }
    }
}

// Function to prompt PIN/Password/Pattern fallback using KeyguardManager
private fun promptPinAuthentication(
    context: Context,
    requestCode: Int,
    title: Int,
    startActivityForResult: (intent: Intent, requestCode: Int) -> Unit,
    onFailure: () -> Unit,
) {
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // For API 23 and above, use isDeviceSecure
        if (keyguardManager.isDeviceSecure) {
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(context.getString(title), null)
            if (intent != null) {
                startActivityForResult(intent, requestCode)
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
                    "Unlock with PIN",
                    "Please unlock to proceed",
                )
            if (intent != null) {
                startActivityForResult(intent, requestCode)
            } else {
                onFailure.invoke()
            }
        } else {
            onFailure.invoke()
        }
    }
}
