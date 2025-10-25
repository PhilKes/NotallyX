package com.philkes.notallyx.utils

import android.app.Activity
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
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.ColorInt
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
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.UnsupportedOperationException
import java.net.URLEncoder
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

fun Context.copyToClipBoard(text: CharSequence) {
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
    val biometricManager = androidx.biometric.BiometricManager.from(this)
    val authenticators =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    return biometricManager.canAuthenticate(authenticators)
}

fun Context.getUriForFile(file: File): Uri =
    FileProvider.getUriForFile(this, "${packageName}.provider", file)

private val LOG_DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

fun Context.getMimeType(uri: Uri) = contentResolver.getType(uri)

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
        return logFile.readText().substringAfterLast("[Start]")
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
        if (throwable != null) {
            Log.e(tag, it, throwable)
        } else if (stackTrace != null) {
            Log.e(tag, "$it: $stackTrace")
        } else {
            Log.i(tag, it)
        }
    }
    throwable?.let { Log.e(tag, "Exception occurred", it) }
    stackTrace?.let { Log.e(tag, "Exception occurred: $it") }

    val logFile =
        folder.findFile(fileName).let {
            if (it == null || !it.exists()) {
                folder.createFileSafe("text/plain", fileName.removeSuffix(".txt"), ".txt")
            } else if (it.isLargerThanKb(MAX_LOGS_FILE_SIZE_KB)) {
                it.delete()
                folder.createFileSafe("text/plain", fileName.removeSuffix(".txt"), ".txt")
            } else it
        }

    logFile?.let { file ->
        val contentResolver = contentResolver
        val (outputStream, logFileContents) =
            try {
                Pair(contentResolver.openOutputStream(file.uri, "wa"), null)
            } catch (e: UnsupportedOperationException) {
                Pair(
                    contentResolver.openOutputStream(file.uri, "w"),
                    contentResolver.readFileContents(file.uri),
                )
            }

        outputStream?.use { output ->
            val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))

            val formatter = DateFormat.getDateTimeInstance()
            val time = formatter.format(System.currentTimeMillis())

            logFileContents?.let { writer.println(it) }
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

fun Fragment.getExtraBooleanFromBundleOrIntent(
    bundle: Bundle?,
    key: String,
    defaultValue: Boolean,
): Boolean {
    return bundle.getExtraBooleanOrDefault(
        key,
        activity?.intent?.getBooleanExtra(key, defaultValue) ?: defaultValue,
    )
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
    fun String?.asQueryParam(paramName: String): String {
        return this?.let { "&$paramName=${URLEncoder.encode(this, "UTF-8")}" } ?: ""
    }
    return Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "https://github.com/PhilKes/NotallyX/issues/new?labels=bug&projects=&template=bug_report.yml${
                    title.asQueryParam("title")
                }&version=${BuildConfig.VERSION_NAME}&android-version=${Build.VERSION.SDK_INT}${
                    stackTrace.asQueryParam("logs")
                }${
                    body.asQueryParam("what-happened")
                    }"
                    .take(2000)
            ),
        )
        .wrapWithChooser(this)
}

fun ContextWrapper.shareNote(note: BaseNote) {
    val body =
        when (note.type) {
            Type.NOTE -> note.body
            Type.LIST -> note.items.toMutableList().toText()
        }
    val filesUris =
        note.images
            .map { File(getExternalImagesDirectory(), it.localName) }
            .map { getUriForFile(it) }
    shareNote(note.title, body, filesUris)
}

private fun Context.shareNote(title: String, body: CharSequence, imageUris: List<Uri>) {
    val text = body.truncate(150_000)
    val intent =
        Intent(if (imageUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
            .apply {
                type = if (imageUris.isEmpty()) "text/*" else "image/*"
                putExtra(Intent.EXTRA_TEXT, text.toString())
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
                if (imageUris.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                } else if (imageUris.isNotEmpty()) {
                    putExtra(Intent.EXTRA_STREAM, imageUris.first())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            .wrapWithChooser(this)
    startActivity(intent)
}

fun Intent.embedIntentExtras() {
    val string = toUri(Intent.URI_INTENT_SCHEME)
    data = Uri.parse(string)
}

fun Context.getOpenNotePendingIntent(note: BaseNote) = getOpenNotePendingIntent(note.id, note.type)

fun Context.getOpenNoteIntent(
    noteId: Long,
    noteType: Type,
    addPendingFlags: Boolean = false,
): Intent {
    return when (noteType) {
        Type.NOTE -> Intent(this, EditNoteActivity::class.java)
        Type.LIST -> Intent(this, EditListActivity::class.java)
    }.apply {
        putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
        if (addPendingFlags) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }
}

fun Context.getOpenNotePendingIntent(noteId: Long, noteType: Type): PendingIntent {
    return PendingIntent.getActivity(
        this,
        0,
        getOpenNoteIntent(noteId, noteType, addPendingFlags = false),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

fun Context.isSystemInDarkMode(): Boolean {
    val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
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

typealias DocumentFolder = DocumentFile

fun DocumentFolder.createFileSafe(
    mimeType: String,
    fileName: String,
    fileExtension: String,
): DocumentFile {
    return requireNotNull(
        createFile(mimeType, fileName + fileExtension) ?: createFile(mimeType, fileName)
    ) {
        "Could not create '$fileName$fileExtension' in Folder '$name' (uri: '$uri')"
    }
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

val isBeforeVanillaIceCream
    get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/** Source: https://stackoverflow.com/a/79286411/9748566 */
@Suppress("Deprecation")
fun Activity.changeStatusAndNavigationBarColor(@ColorInt color: Int) {
    window.statusBarColor = color
    window.navigationBarColor = color
    if (!isBeforeVanillaIceCream) {
        window.decorView.setBackgroundColor(color)
        window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        window.insetsController?.show(android.view.WindowInsets.Type.statusBars())
    }
}

fun Activity.resetApplication() {
    val resetApplicationIntent = packageManager.getLaunchIntentForPackage(packageName)
    resetApplicationIntent?.setFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    )
    startActivity(resetApplicationIntent)
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}

fun Bundle?.getExtraBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
    return this?.getBoolean(key, defaultValue) ?: defaultValue
}

fun ContentResolver.readFileContents(uri: Uri) =
    openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader -> reader.readText() }
    } ?: ""
