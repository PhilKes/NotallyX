package com.philkes.notallyx.utils

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.philkes.notallyx.BuildConfig
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        else -> uri.path?.let { File(it) }?.name
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

fun Context.getUriForFile(file: File): Uri =
    FileProvider.getUriForFile(this, "${packageName}.provider", file)

private val LOG_DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

fun ContextWrapper.log(
    tag: String,
    msg: String? = null,
    throwable: Throwable? = null,
    stackTrace: String? = null,
) {
    val folder = getLogsDir()
    folder.mkdir()
    logToFile(tag, DocumentFile.fromFile(folder), APP_LOG_FILE_NAME, msg, throwable, stackTrace)
}

fun ContextWrapper.getLastExceptionLog(): String? {
    val logFile = getLogFile()
    if (logFile.exists()) {
        val logContents = logFile.readText().substringAfterLast("[Start]")
        return URLEncoder.encode(logContents, StandardCharsets.UTF_8.toString())
    }
    return null
}

private const val MAX_LOGS_FILE_SIZE_KB: Long = 2048

fun Context.logToFile(
    tag: String,
    folder: DocumentFile,
    fileName: String,
    msg: String? = null,
    throwable: Throwable? = null,
    stackTrace: String? = null,
) {
    msg?.let {
        if (throwable != null || stackTrace != null) {
            Log.e(tag, it)
        } else {
            Log.i(tag, it)
        }
    }
    throwable?.let { Log.e(tag, "Exception occurred", it) }
    stackTrace?.let { Log.e(tag, "Exception occurred: $it") }

    val logFile =
        folder.findFile(fileName).let {
            if (it == null || !it.exists()) {
                folder.createFile("text/plain", fileName)
            } else if (it.isLargerThanKb(MAX_LOGS_FILE_SIZE_KB)) {
                it.delete()
                folder.createFile("text/plain", fileName)
            } else it
        }

    logFile?.let { file ->
        val contentResolver = contentResolver
        val outputStream = contentResolver.openOutputStream(file.uri, "wa")

        outputStream?.use { output ->
            val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))

            val formatter = DateFormat.getDateTimeInstance()
            val time = formatter.format(System.currentTimeMillis())

            if (throwable != null || stackTrace != null) {
                writer.println("[Start]")
            }
            msg?.let { writer.println("${LOG_DATE_FORMATTER.format(Date())} - $tag - $msg") }
            throwable?.printStackTrace(writer)
            stackTrace?.let { writer.println(it) }
            if (throwable != null || stackTrace != null) {
                writer.println("Version code : " + BuildConfig.VERSION_CODE)
                writer.println("Version name : " + BuildConfig.VERSION_NAME)
                writer.println("Model : " + Build.MODEL)
                writer.println("Device : " + Build.DEVICE)
                writer.println("Brand : " + Build.BRAND)
                writer.println("Manufacturer : " + Build.MANUFACTURER)
                writer.println("Android : " + Build.VERSION.SDK_INT)
                writer.println("Time : $time")
                writer.println("[End]")
            }

            writer.close()
        } ?: Log.e(tag, "Error opening output stream for log file")
    } ?: Log.e(tag, "Error: log file could not be found or created")
}

fun Fragment.reportBug(stackTrace: String?) {
    requireContext().catchNoBrowserInstalled {
        startActivity(requireContext().createReportBugIntent(stackTrace))
    }
}

fun Context.reportBug(stackTrace: String?) {
    catchNoBrowserInstalled { startActivity(createReportBugIntent(stackTrace)) }
}

fun Context.catchNoBrowserInstalled(callback: () -> Unit) {
    try {
        callback()
    } catch (exception: ActivityNotFoundException) {
        showToast(R.string.install_a_browser)
    }
}

fun Context.createReportBugIntent(
    stackTrace: String?,
    title: String? = null,
    body: String? = null,
): Intent {
    return Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://github.com/PhilKes/NotallyX/issues/new?labels=bug&projects=&template=bug_report.yml${title?.let { "&title=$it" }}${body?.let { "&what-happened=$it" }}&version=${BuildConfig.VERSION_NAME}&android-version=${Build.VERSION.SDK_INT}${stackTrace?.let { "&logs=$stackTrace" } ?: ""}"
                    .take(2000)
            ),
        )
        .wrapWithChooser(this)
}

fun Context.shareNote(title: String, body: CharSequence) {
    val text = body.truncate(150_000)

    val intent =
        Intent(Intent.ACTION_SEND)
            .apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.toString())
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            .wrapWithChooser(this)
    startActivity(intent)
}

fun Intent.embedIntentExtras() {
    val string = toUri(Intent.URI_INTENT_SCHEME)
    data = Uri.parse(string)
}

fun Context.getOpenNoteIntent(note: BaseNote) = getOpenNoteIntent(note.id, note.type)

fun Context.getOpenNoteIntent(noteId: Long, noteType: Type): PendingIntent {
    val intent =
        when (noteType) {
            Type.NOTE -> Intent(this, EditNoteActivity::class.java)
            Type.LIST -> Intent(this, EditListActivity::class.java)
        }.apply {
            putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    return PendingIntent.getActivity(
        this,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

fun ContentResolver.determineMimeTypeAndExtension(uri: Uri, proposedMimeType: String?) =
    if (proposedMimeType != null && proposedMimeType.contains("/")) {
        Pair(proposedMimeType, ".${uri.lastPathSegment?.substringAfterLast(".")}")
    } else {
        val actualMimeType = getType(uri) ?: "application/octet-stream"
        Pair(
            actualMimeType,
            MimeTypeMap.getSingleton().getExtensionFromMimeType(actualMimeType)?.let { ".${it}" }
                ?: "",
        )
    }

fun Intent.wrapWithChooser(context: Context, titleResId: Int? = null): Intent =
    Intent.createChooser(this, titleResId?.let { context.getText(it) })

fun DocumentFile.isLargerThanKb(kilobytes: Long): Boolean {
    return (length() / 1024.0) > kilobytes
}

fun DocumentFile.listZipFiles(prefix: String): List<DocumentFile> {
    if (!this.isDirectory) return emptyList()
    val zipFiles =
        this.listFiles().filter { file ->
            file.isFile &&
                file.name?.endsWith(".zip", ignoreCase = true) == true &&
                file.name?.startsWith(prefix, ignoreCase = true) == true
        }
    return zipFiles.sortedByDescending { it.lastModified() }
}

val DocumentFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast(".")

@RequiresApi(Build.VERSION_CODES.O)
fun NotificationManager.createChannelIfNotExists(
    channelId: String,
    importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
) {
    if (getNotificationChannel(channelId) == null) {
        val channel = NotificationChannel(channelId, channelId, importance)
        createNotificationChannel(channel)
    }
}

fun <T> LiveData<T>.observeOnce(observer: Observer<T>) {
    val wrapperObserver =
        object : Observer<T> {
            override fun onChanged(value: T) {
                this@observeOnce.removeObserver(this)
                observer.onChanged(value)
            }
        }
    this.observeForever(wrapperObserver)
}

fun Uri.toReadablePath(): String {
    return path!!
        .replaceFirst("/tree/primary:", "Internal Storage/")
        .replaceFirst("/tree/.*:".toRegex(), "External Storage/")
}
