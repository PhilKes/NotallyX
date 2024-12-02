package com.philkes.notallyx.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import java.io.File

fun <T> createObserverSkipFirst(observer: Observer<T>): Observer<T> {
    return object : Observer<T> {
        private var isFirstEvent = true

        override fun onChanged(value: T) {
            if (isFirstEvent) {
                isFirstEvent = false
            } else {
                observer.onChanged(value)
            }
        }
    }
}

fun <T> LiveData<T>.observeSkipFirst(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    this.observe(lifecycleOwner, createObserverSkipFirst(observer))
}

fun <T> LiveData<T>.observeForeverSkipFirst(observer: Observer<T>) {
    var isFirstEvent = true
    this.observeForever { value ->
        if (isFirstEvent) {
            isFirstEvent = false
        } else {
            observer.onChanged(value)
        }
    }
}

fun <T, C> NotNullLiveData<T>.mergeSkipFirst(
    liveData: NotNullLiveData<C>
): MediatorLiveData<Pair<T, C>> {
    return MediatorLiveData<Pair<T, C>>().apply {
        addSource(
            this@mergeSkipFirst,
            createObserverSkipFirst { value1 -> value = Pair(value1, liveData.value) },
        )
        addSource(
            liveData,
            createObserverSkipFirst { value2 -> value = Pair(this@mergeSkipFirst.value, value2) },
        )
    }
}

fun ClipboardManager.getLatestText(): CharSequence? {
    return primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0)!!.text else null }
}

fun Activity.copyToClipBoard(text: CharSequence) {
    ContextCompat.getSystemService(this, ClipboardManager::class.java)?.let {
        val clip = ClipData.newPlainText("label", text)
        it.setPrimaryClip(clip)
    }
}

fun Context.getContentFileName(uri: Uri): String? =
    runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                return@use cursor
                    .getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    .let(cursor::getString)
            }
        }
        .getOrNull()

fun Context.getFileName(uri: Uri): String? =
    when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
        else -> uri.path?.let(::File)?.name
    }

fun Context.canAuthenticateWithBiometrics(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val keyguardManager = ContextCompat.getSystemService(this, KeyguardManager::class.java)
            val packageManager: PackageManager = this.packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            }
            if (keyguardManager?.isKeyguardSecure == false) {
                return BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val biometricManager: BiometricManager =
                this.getSystemService(BiometricManager::class.java)
            return biometricManager.canAuthenticate()
        } else {
            val biometricManager: BiometricManager =
                this.getSystemService(BiometricManager::class.java)
            return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        }
    }
    return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
}

fun String.truncate(limit: Int): String {
    return if (length > limit) {
        val truncated = take(limit)
        val remainingCharacters = length - limit
        "$truncated... ($remainingCharacters more characters)"
    } else {
        this
    }
}

fun String.findAllOccurrences(
    search: String,
    caseSensitive: Boolean = false,
): List<Pair<Int, Int>> {
    if (search.isEmpty()) return emptyList()
    val regex = Regex(Regex.escape(if (caseSensitive) search else search.lowercase()))
    return regex
        .findAll(if (caseSensitive) this else this.lowercase())
        .map { match -> match.range.first to match.range.last + 1 }
        .toList()
}
