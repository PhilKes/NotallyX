package com.philkes.notallyx.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
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
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.getUrl
import com.philkes.notallyx.databinding.DialogColorBinding
import com.philkes.notallyx.databinding.DialogProgressBinding
import com.philkes.notallyx.presentation.view.main.ColorAdapter
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange
import java.io.File
import java.util.Date
import me.zhanghai.android.fastscroll.FastScrollerBuilder
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

fun String.truncate(limit: Int): String {
    return if (length > limit) {
        val truncated = take(limit)
        val remainingCharacters = length - limit
        "$truncated... ($remainingCharacters more characters)"
    } else {
        this
    }
}

fun String.removeTrailingParentheses(): String {
    return substringBeforeLast(" (")
}

/**
 * Adjusts or removes spans based on the selection range.
 *
 * @param selectionStart the start index of the selection
 * @param selectionEnd the end index of the selection
 */
fun Editable.removeSelectionFromSpans(
    selectionStart: Int,
    selectionEnd: Int,
    spans: Collection<CharacterStyle> =
        getSpans(selectionStart, selectionEnd, CharacterStyle::class.java).toList(),
) {
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

fun Menu.add(
    title: Int,
    drawable: Int,
    showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM,
    groupId: Int = Menu.NONE,
    order: Int = Menu.NONE,
    onClick: (item: MenuItem) -> Unit,
): MenuItem {
    val menuItem =
        add(groupId, Menu.NONE, order, title).setIcon(drawable).setOnMenuItemClickListener { item ->
            onClick(item)
            item.isChecked = true
            return@setOnMenuItemClickListener false
        }
    menuItem.setShowAsAction(showAsAction)
    return menuItem
}

fun ViewGroup.addIconButton(
    title: Int,
    drawable: Int,
    marginStart: Int = 10,
    onClick: ((item: View) -> Unit)? = null,
): View {
    val view =
        ImageButton(ContextThemeWrapper(context, R.style.AppTheme)).apply {
            setImageResource(drawable)
            contentDescription = context.getString(title)
            setBackgroundResource(R.color.Transparent)
            setOnClickListener(onClick)
            layoutParams =
                LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    .apply { setMargins(marginStart.dp(context), marginTop, 0, marginBottom) }
        }
    addView(view)
    return view
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
 * @param onTextChanged optional text change handler. Returns whether or not the original change
 *   should be ignored or not.
 */
fun EditText.createListTextWatcherWithHistory(
    listManager: ListManager,
    positionGetter: () -> Int,
    onTextChanged: ((text: CharSequence, start: Int, count: Int) -> Boolean)? = null,
) =
    object : TextWatcher {
        private lateinit var stateBefore: EditTextState
        private var ignoreOriginalChange: Boolean = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            stateBefore = EditTextState(getText()!!.clone(), selectionStart)
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            ignoreOriginalChange = onTextChanged?.invoke(s!!, start, count) ?: false
        }

        override fun afterTextChanged(s: Editable?) {
            if (!ignoreOriginalChange) {
                listManager.changeText(
                    this@createListTextWatcherWithHistory,
                    this,
                    positionGetter.invoke(),
                    EditTextState(getText()!!.clone(), selectionStart),
                    before = stateBefore,
                )
            }
        }
    }

fun StylableEditTextWithHistory.createTextWatcherWithHistory(
    changeHistory: ChangeHistory,
    onTextChanged: ((text: CharSequence, start: Int, count: Int) -> Unit)? = null,
    updateModel: (text: Editable) -> Unit,
) =
    object : TextWatcher {
        private lateinit var stateBefore: EditTextState

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            stateBefore = EditTextState(getTextClone(), selectionStart)
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onTextChanged?.invoke(s!!, start, count)
        }

        override fun afterTextChanged(s: Editable?) {
            val textAfter = requireNotNull(s).clone()
            updateModel.invoke(textAfter)
            changeHistory.push(
                EditTextWithHistoryChange(
                    this@createTextWatcherWithHistory,
                    stateBefore,
                    EditTextState(textAfter, selectionStart),
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

fun Activity.showColorSelectDialog(callback: (selectedColor: Color) -> Unit) {
    val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.change_color).create()

    val colorAdapter =
        ColorAdapter(
            object : ItemListener {
                override fun onClick(position: Int) {
                    dialog.dismiss()
                    callback(Color.entries[position])
                }

                override fun onLongClick(position: Int) {}
            }
        )

    DialogColorBinding.inflate(layoutInflater).apply {
        RecyclerView.adapter = colorAdapter
        dialog.setView(root)
        dialog.show()
    }
}

fun MaterialAlertDialogBuilder.showAndFocus(view: View, selectAll: Boolean = false): AlertDialog {
    return create().apply {
        view.requestFocus()
        if (view is EditText) {
            if (selectAll) {
                view.selectAll()
            }
            window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE)
        }
        show()
    }
}

fun Context.getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any): String {
    return resources.getQuantityString(id, quantity, quantity, *formatArgs)
}

@ColorInt
fun @receiver:ColorInt Int.withAlpha(alpha: Float): Int {
    return android.graphics.Color.argb(
        (255 * alpha).toInt(),
        android.graphics.Color.red(this),
        android.graphics.Color.green(this),
        android.graphics.Color.blue(this),
    )
}

fun Context.getUriForFile(file: File): Uri =
    FileProvider.getUriForFile(this, "${packageName}.provider", file)

val DocumentFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast(".")

fun Context.getColorFromAttr(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    val resolved = theme.resolveAttribute(attr, typedValue, true)
    if (resolved) {
        return typedValue.data // Returns the color as an Int
    } else {
        throw IllegalArgumentException("Attribute not found in current theme")
    }
}

fun View.setControlsContrastColorForAllViews(@ColorInt backgroundColor: Int) {
    val controlsColor = context.getContrastFontColor(backgroundColor)
    setControlsColorForAllViews(controlsColor, backgroundColor)
}

fun View.setControlsColorForAllViews(@ColorInt controlsColor: Int, @ColorInt backgroundColor: Int) {
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.setControlsColorForAllViews(
                controlsColor,
                backgroundColor,
            ) // Recursive call for nested layouts
        }
    } else {
        if (this is Chip) {
            // Chip should not be re-colored
            return
        }
        if (this is TextView) {
            setCompoundDrawableTint(controlsColor)
            setTextColor(controlsColor)
            setLinkTextColor(controlsColor)
            setHintTextColor(controlsColor.withAlpha(0.5f))
        }
        if (this is CompoundButton) {
            buttonTintList = ColorStateList.valueOf(controlsColor)
        }
        if (this is ImageButton) {
            imageTintList = ColorStateList.valueOf(controlsColor)
        }
        if (this is MaterialCheckBox) {
            buttonIconTintList = ColorStateList.valueOf(backgroundColor)
        }
        backgroundTintList = ColorStateList.valueOf(controlsColor)
    }
}

fun TextView.setCompoundDrawableTint(@ColorInt color: Int) {
    compoundDrawablesRelative.forEach { drawable ->
        drawable?.let { DrawableCompat.setTint(DrawableCompat.wrap(it), color) }
    }

    compoundDrawables.forEach { drawable ->
        drawable?.let { DrawableCompat.setTint(DrawableCompat.wrap(it), color) }
    }

    (background as? MaterialShapeDrawable)?.setStrokeTint(color)

    //    setCompoundDrawablesRelativeWithIntrinsicBounds(
    //        compoundDrawablesRelative[0], // Start
    //        compoundDrawablesRelative[1], // Top
    //        compoundDrawablesRelative[2], // End
    //        compoundDrawablesRelative[3], // Bottom
    //    )
}

@ColorInt
private fun Context.getContrastFontColor(@ColorInt backgroundColor: Int): Int {
    // Extract RGB components
    val red = android.graphics.Color.red(backgroundColor) / 255.0
    val green = android.graphics.Color.green(backgroundColor) / 255.0
    val blue = android.graphics.Color.blue(backgroundColor) / 255.0

    // Calculate relative luminance
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue

    // Return white for dark backgrounds, black for light backgrounds
    return if (luminance > 0.5) ContextCompat.getColor(this, R.color.TextDark)
    else ContextCompat.getColor(this, R.color.TextLight)
}

fun MaterialAlertDialogBuilder.addCancelButton(): MaterialAlertDialogBuilder {
    setNegativeButton(R.string.cancel, null)
    return this
}

fun Fragment.showDialog(
    messageResId: Int,
    positiveButtonTextResId: Int,
    onPositiveButtonClickListener: DialogInterface.OnClickListener,
) =
    requireContext()
        .showDialog(messageResId, positiveButtonTextResId, onPositiveButtonClickListener)

fun Context.showDialog(
    messageResId: Int,
    positiveButtonTextResId: Int,
    onPositiveButtonClickListener: DialogInterface.OnClickListener,
) {
    MaterialAlertDialogBuilder(this)
        .setMessage(messageResId)
        .setPositiveButton(positiveButtonTextResId, onPositiveButtonClickListener)
        .addCancelButton()
        .show()
}

fun Fragment.showToast(messageResId: Int) = requireContext().showToast(messageResId)

fun Context.showToast(messageResId: Int) =
    ContextCompat.getMainExecutor(this).execute {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show()
    }

fun Context.showToast(message: CharSequence) =
    ContextCompat.getMainExecutor(this).execute {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

fun ViewGroup.addFastScroll(context: Context) {
    FastScrollerBuilder(this)
        .useMd2Style()
        .setTrackDrawable(ContextCompat.getDrawable(context, R.drawable.scroll_track)!!)
        .setPadding(0, 0, 2.dp(context), 0)
        .build()
}
