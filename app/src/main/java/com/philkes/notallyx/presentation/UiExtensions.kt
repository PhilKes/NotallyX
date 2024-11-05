package com.philkes.notallyx.presentation

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.getUrl
import com.philkes.notallyx.databinding.DialogProgressBinding
import com.philkes.notallyx.presentation.view.misc.EditTextWithHistory
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange
import java.io.File
import java.util.Date
import org.ocpsoft.prettytime.PrettyTime

/**
 * For some reason, this method crashes sometimes with an IndexOutOfBoundsException that I've not
 * been able to replicate. When this happens, to prevent the entire app from crashing and becoming
 * unusable, the exception is suppressed.
 */
fun String.applySpans(representations: List<SpanRepresentation>): Editable {
    val editable = Editable.Factory.getInstance().newEditable(this)
    representations.forEach { (start, end, bold, link, linkData, italic, monospace, strikethrough)
        ->
        try {
            if (bold) {
                editable.setSpan(StyleSpan(Typeface.BOLD), start, end)
            }
            if (italic) {
                editable.setSpan(StyleSpan(Typeface.ITALIC), start, end)
            }
            if (link) {
                val url = linkData ?: getUrl(start, end)
                editable.setSpan(URLSpan(url), start, end)
            }
            if (monospace) {
                editable.setSpan(TypefaceSpan("monospace"), start, end)
            }
            if (strikethrough) {
                editable.setSpan(StrikethroughSpan(), start, end)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
    return editable
}

/**
 * Adjusts or removes spans based on the selection range.
 *
 * @param selectionStart the start index of the selection
 * @param selectionEnd the end index of the selection
 */
fun Editable.removeSelectionFromSpan(selectionStart: Int, selectionEnd: Int) {
    // Get all spans of type CharacterStyle (can be extended to other types)
    val spans = getSpans(selectionStart, selectionEnd, CharacterStyle::class.java)

    for (span in spans) {
        val spanStart = getSpanStart(span)
        val spanEnd = getSpanEnd(span)

        when {
            // Case 1: Selection is exactly the span's range (remove entire span)
            selectionStart <= spanStart && selectionEnd >= spanEnd -> {
                removeSpan(span)
            }
            // Case 2: Selection is part of the span (split the span)
            selectionStart > spanStart && selectionEnd < spanEnd -> {
                // Remove the original span
                removeSpan(span)
                // Reapply two new spans: one for the part before the selection, and one for the
                // part after
                setSpan(span, spanStart, selectionStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(span, selectionEnd, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Case 3: Selection overlaps the start of the span (trim the span)
            selectionStart <= spanStart && selectionEnd < spanEnd -> {
                removeSpan(span)
                setSpan(span, selectionEnd, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // Case 4: Selection overlaps the end of the span (trim the span)
            else -> {
                removeSpan(span)
                setSpan(span, spanStart, selectionStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

private fun Spannable.setSpan(span: Any, start: Int, end: Int) {
    if (end <= length) {
        setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    } else setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

fun EditText.setOnNextAction(onNext: () -> Unit) {
    setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)

    setOnKeyListener { v, keyCode, event ->
        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
            onNext()
            return@setOnKeyListener true
        } else return@setOnKeyListener false
    }

    setOnEditorActionListener { v, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_NEXT) {
            onNext()
            return@setOnEditorActionListener true
        } else return@setOnEditorActionListener false
    }
}

fun Menu.add(title: Int, drawable: Int, onClick: (item: MenuItem) -> Unit): MenuItem {
    return add(Menu.NONE, title, drawable, MenuItem.SHOW_AS_ACTION_IF_ROOM, onClick)
}

fun Menu.add(
    title: Int,
    drawable: Int,
    showAsAction: Int,
    onClick: (item: MenuItem) -> Unit,
): MenuItem {
    return add(Menu.NONE, title, drawable, showAsAction, onClick)
}

fun Menu.add(
    groupId: Int,
    title: Int,
    drawable: Int,
    showAsAction: Int,
    onClick: (item: MenuItem) -> Unit,
): MenuItem {
    val menuItem = add(groupId, Menu.NONE, Menu.NONE, title)
    menuItem.setIcon(drawable)
    menuItem.setOnMenuItemClickListener { item ->
        onClick(item)
        return@setOnMenuItemClickListener false
    }
    menuItem.setShowAsAction(showAsAction)
    return menuItem
}

fun TextView.displayFormattedTimestamp(
    timestamp: Long?,
    dateFormat: DateFormat,
    prefixResId: Int? = null,
) {
    if (dateFormat != DateFormat.NONE && timestamp != null) {
        visibility = View.VISIBLE
        text =
            "${prefixResId?.let { getString(it) } ?: ""} ${formatTimestamp(timestamp, dateFormat)}"
    } else visibility = View.GONE
}

fun Int.dp(context: Context): Int =
    TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics,
        )
        .toInt()

/**
 * Creates a TextWatcher for an EditText that is part of a list. Everytime the text is changed, a
 * Change is added to the ChangeHistory.
 *
 * @param positionGetter Function to determine the current position of the EditText in the list
 *   (e.g. the current adapterPosition when using RecyclerViewer.Adapter)
 * @param updateModel Function to update the model. Is called on any text changes and on undo/redo.
 */
fun EditText.createListTextWatcherWithHistory(listManager: ListManager, positionGetter: () -> Int) =
    object : TextWatcher {
        private lateinit var currentTextBefore: String

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            currentTextBefore = s.toString()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            listManager.changeText(
                this@createListTextWatcherWithHistory,
                this,
                positionGetter.invoke(),
                currentTextBefore,
                requireNotNull(s).toString(),
            )
        }
    }

fun EditTextWithHistory.createTextWatcherWithHistory(
    changeHistory: ChangeHistory,
    onTextChanged: ((text: CharSequence, start: Int, count: Int) -> Unit)? = null,
    updateModel: (text: Editable) -> Unit,
) =
    object : TextWatcher {
        private lateinit var currentTextBefore: Editable

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            currentTextBefore = this@createTextWatcherWithHistory.getTextClone()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged?.invoke(s!!, start, count)
        }

        override fun afterTextChanged(s: Editable?) {
            val textBefore = currentTextBefore.clone()
            val textAfter = requireNotNull(s).clone()
            updateModel.invoke(textAfter)

            changeHistory.push(
                EditTextWithHistoryChange(
                    this@createTextWatcherWithHistory,
                    textBefore,
                    textAfter,
                    updateModel,
                )
            )
        }
    }

fun Editable.clone(): Editable = Editable.Factory.getInstance().newEditable(this)

fun View.getString(id: Int, vararg formatArgs: String): String {
    return context.resources.getString(id, *formatArgs)
}

fun View.getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
    return context.getQuantityString(id, quantity, *formatArgs)
}

fun Folder.movedToResId(): Int {
    return when (this) {
        Folder.DELETED -> R.plurals.deleted_selected_notes
        Folder.ARCHIVED -> R.plurals.archived_selected_notes
        Folder.NOTES -> R.plurals.restored_selected_notes
    }
}

fun RadioGroup.checkedTag(): Any {
    return this.findViewById<RadioButton?>(this.checkedRadioButtonId).tag
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
            return biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        }
    }
    return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
}

fun Context.getFileName(uri: Uri): String? =
    when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT -> getContentFileName(uri)
        else -> uri.path?.let(::File)?.name
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

fun Activity.showKeyboard(view: View) {
    ContextCompat.getSystemService(this, InputMethodManager::class.java)
        ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun MutableLiveData<out Progress>.setupProgressDialog(activity: Activity, titleId: Int) {
    setupProgressDialog(activity, activity.layoutInflater, activity as LifecycleOwner, titleId)
}

fun MutableLiveData<out Progress>.setupProgressDialog(fragment: Fragment, titleId: Int) {
    setupProgressDialog(
        fragment.requireContext(),
        fragment.layoutInflater,
        fragment.viewLifecycleOwner,
        titleId,
    )
}

fun MutableLiveData<ImportProgress>.setupImportProgressDialog(fragment: Fragment, titleId: Int) {
    setupProgressDialog(
        fragment.requireContext(),
        fragment.layoutInflater,
        fragment.viewLifecycleOwner,
        titleId,
    ) { context, binding, progress ->
        val stageStr =
            context.getString(
                when (progress.stage) {
                    ImportStage.IMPORT_NOTES -> R.string.imported_notes
                    ImportStage.EXTRACT_FILES -> R.string.extracted_files
                    ImportStage.IMPORT_FILES -> R.string.imported_files
                }
            )
        binding.Count.text =
            "${context.getString(R.string.count, progress.current, progress.total)} $stageStr"
    }
}

private fun <T : Progress> MutableLiveData<T>.setupProgressDialog(
    context: Context,
    layoutInflater: LayoutInflater,
    viewLifecycleOwner: LifecycleOwner,
    titleId: Int,
    renderProgress: ((context: Context, binding: DialogProgressBinding, progress: T) -> Unit)? =
        null,
) {
    val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
    val dialog =
        MaterialAlertDialogBuilder(context)
            .setTitle(titleId)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

    observe(viewLifecycleOwner) { progress ->
        if (progress.inProgress) {
            if (progress.indeterminate) {
                dialogBinding.apply {
                    ProgressBar.isIndeterminate = true
                    Count.setText(R.string.calculating)
                }
            } else {
                dialogBinding.apply {
                    ProgressBar.apply {
                        isIndeterminate = false
                        max = progress.total
                        setProgressCompat(progress.current, true)
                    }
                    if (renderProgress == null) {
                        Count.text =
                            context.getString(R.string.count, progress.current, progress.total)
                    } else renderProgress.invoke(context, this, progress)
                }
            }
            dialog.show()
        } else dialog.dismiss()
    }
}

fun Activity.checkNotificationPermission(
    requestCode: Int,
    messageResId: Int,
    onSuccess: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(messageResId)
                    .setNegativeButton(R.string.cancel) { _, _ -> onSuccess() }
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), requestCode)
                    }
                    .setOnDismissListener { onSuccess() }
                    .show()
            } else requestPermissions(arrayOf(permission), requestCode)
        } else onSuccess()
    } else onSuccess()
}

private fun formatTimestamp(timestamp: Long, dateFormat: DateFormat): String {
    val date = Date(timestamp)
    return when (dateFormat) {
        DateFormat.RELATIVE -> PrettyTime().format(date)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date)
    }
}

fun Activity.copyToClipBoard(text: CharSequence) {
    ContextCompat.getSystemService(this, ClipboardManager::class.java)?.let {
        val clip = ClipData.newPlainText("label", text)
        it.setPrimaryClip(clip)
    }
}

fun ClipboardManager.getLatestText(): CharSequence? {
    return if (primaryClip!!.itemCount > 0) primaryClip!!.getItemAt(0)!!.text else null
}

fun MaterialAlertDialogBuilder.showAndFocus(view: View): AlertDialog {
    val dialog = show()
    view.requestFocus()
    if (view is EditText) {
        dialog.window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE)
    }
    return dialog
}

fun Context.getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
    return resources.getQuantityString(id, quantity, quantity, *formatArgs)
}
