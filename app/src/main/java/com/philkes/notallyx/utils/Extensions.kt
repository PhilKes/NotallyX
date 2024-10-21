package com.philkes.notallyx.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Typeface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.os.Build
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
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import androidx.appcompat.app.AppCompatActivity.KEYGUARD_SERVICE
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.view.misc.DateFormat
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextChange
import java.util.Date
import kotlin.math.roundToInt
import org.ocpsoft.prettytime.PrettyTime

/**
 * For some reason, this method crashes sometimes with an IndexOutOfBoundsException that I've not
 * been able to replicate. When this happens, to prevent the entire app from crashing and becoming
 * unusable, the exception is suppressed.
 */
fun String.applySpans(representations: List<SpanRepresentation>): Editable {
    val editable = Editable.Factory.getInstance().newEditable(this)
    representations.forEach { (bold, link, linkData, italic, monospace, strikethrough, start, end)
        ->
        try {
            if (bold) {
                editable.setSpan(StyleSpan(Typeface.BOLD), start, end)
            }
            if (italic) {
                editable.setSpan(StyleSpan(Typeface.ITALIC), start, end)
            }
            if (link) {
                val url = linkData ?: getURL(start, end)
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
 * Extension function for Editable to modify or remove spans based on the selection range.
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

private fun String.getURL(start: Int, end: Int): String {
    return if (end <= length) {
        EditNoteActivity.getURLFrom(substring(start, end))
    } else EditNoteActivity.getURLFrom(substring(start, length))
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

fun TextView.displayFormattedTimestamp(timestamp: Long, dateFormat: String) {
    if (dateFormat != DateFormat.none) {
        visibility = View.VISIBLE
        text = formatTimestamp(timestamp, dateFormat)
    } else visibility = View.GONE
}

fun RemoteViews.displayFormattedTimestamp(id: Int, timestamp: Long, dateFormat: String) {
    if (dateFormat != DateFormat.none) {
        setViewVisibility(id, View.VISIBLE)
        setTextViewText(id, formatTimestamp(timestamp, dateFormat))
    } else setViewVisibility(id, View.GONE)
}

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).roundToInt()

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

fun EditText.createTextWatcherWithHistory(
    changeHistory: ChangeHistory,
    onTextChanged: ((text: CharSequence, start: Int, count: Int) -> Unit)? = null,
    updateModel: (text: Editable) -> Unit,
) =
    object : TextWatcher {
        private lateinit var currentTextBefore: Editable

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            currentTextBefore = this@createTextWatcherWithHistory.text.clone()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged?.invoke(s!!, start, count)
        }

        override fun afterTextChanged(s: Editable?) {
            val textBefore = currentTextBefore.clone()
            val textAfter = requireNotNull(s).clone()
            updateModel.invoke(textAfter)

            changeHistory.push(
                EditTextChange(
                    this@createTextWatcherWithHistory,
                    textBefore,
                    textAfter,
                    this,
                    updateModel,
                )
            )
        }
    }

fun Editable.clone(): Editable = Editable.Factory.getInstance().newEditable(this)

fun View.getQuantityString(id: Int, quantity: Int): String {
    return context.resources.getQuantityString(id, quantity, quantity)
}

fun View.getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
    return context.resources.getQuantityString(id, quantity, *formatArgs)
}

val FileAttachment.isImage: Boolean
    get() {
        return mimeType.startsWith("image/")
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
    var canAuthenticate = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val keyguardManager: KeyguardManager =
                this.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            val packageManager: PackageManager = this.packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                return BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            }
            if (!keyguardManager.isKeyguardSecure) {
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

val String.toPreservedByteArray: ByteArray
    get() {
        return this.toByteArray(Charsets.ISO_8859_1)
    }

val ByteArray.toPreservedString: String
    get() {
        return String(this, Charsets.ISO_8859_1)
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

fun Activity.showKeyboard(view: View) {
    val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

private fun formatTimestamp(timestamp: Long, dateFormat: String): String {
    val date = Date(timestamp)
    return when (dateFormat) {
        DateFormat.relative -> PrettyTime().format(date)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date)
    }
}
