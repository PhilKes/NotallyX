package com.philkes.notallyx.presentation

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuggestionSpan
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
import android.view.Window
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.text.getSpans
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.databinding.DialogInputBinding
import com.philkes.notallyx.databinding.DialogProgressBinding
import com.philkes.notallyx.databinding.LabelBinding
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemVH
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange
import com.philkes.notallyx.utils.getUrl
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
    onLongClick: View.OnLongClickListener? = null,
    onClick: View.OnClickListener? = null,
): ImageButton {
    val view =
        ImageButton(ContextThemeWrapper(context, R.style.AppTheme)).apply {
            setImageResource(drawable)
            contentDescription = context.getString(title)
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnLongClickListener(onLongClick)
            setOnClickListener(onClick)

            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams =
                LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    )
                    .apply { setMargins(marginStart.dp, marginTop, 0, marginBottom) }
            setPadding(8.dp)
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

val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

val Float.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

/**
 * Creates a TextWatcher for an EditText that is part of a list. Everytime the text is changed, a
 * Change is added to the ChangeHistory.
 *
 * @param positionGetter Function to determine the current position of the EditText in the list
 *   (e.g. the current absoluteAdapterPosition when using RecyclerViewer.Adapter)
 * @param onTextChanged optional text change handler. Returns whether or not the original change
 *   should be ignored or not.
 */
fun EditText.createListTextWatcherWithHistory(
    listManager: ListManager,
    positionGetter: () -> Int,
    onTextChanged: ((text: CharSequence, start: Int, count: Int) -> Boolean)? = null,
) =
    object : TextWatcher {
        private var ignoreOriginalChange: Boolean = false
        private lateinit var textBefore: Editable

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            textBefore = text.clone()
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            ignoreOriginalChange = onTextChanged?.invoke(s!!, start, count) ?: false
        }

        override fun afterTextChanged(s: Editable?) {
            val textAfter = s!!.clone()
            if (textAfter.hasNotChanged(textBefore)) {
                return
            }
            if (!ignoreOriginalChange) {
                listManager.changeText(
                    positionGetter.invoke(),
                    EditTextState(textAfter, selectionStart),
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
            if (textAfter.hasNotChanged(stateBefore.text)) {
                return
            }
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

fun Editable.hasNotChanged(before: Editable): Boolean {
    return toString() == before.toString() && getSpans<SuggestionSpan>().isNotEmpty()
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

fun Context.showKeyboard(view: View) {
    ContextCompat.getSystemService(this, InputMethodManager::class.java)
        ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun Context.hideKeyboard(view: View) {
    ContextCompat.getSystemService(this, InputMethodManager::class.java)
        ?.hideSoftInputFromWindow(view.windowToken, 0)
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

fun <T, C> NotNullLiveData<T>.merge(liveData: NotNullLiveData<C>): MediatorLiveData<Pair<T, C>> {
    return MediatorLiveData<Pair<T, C>>().apply {
        addSource(this@merge) { value1 -> value = Pair(value1, liveData.value) }
        addSource(liveData) { value2 -> value = Pair(this@merge.value, value2) }
    }
}

fun <T, C> NotNullLiveData<T>.merge(liveData: LiveData<C>): MediatorLiveData<Pair<T, C?>> {
    return MediatorLiveData<Pair<T, C?>>().apply {
        addSource(this@merge) { value1 -> value = Pair(value1, liveData.value) }
        addSource(liveData) { value2 -> value = Pair(this@merge.value, value2) }
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
    alsoCheckAlarmPermission: Boolean = false,
    alarmPermissionResultLauncher: ActivityResultLauncher<Intent>? = null,
    onSuccess: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_notification)
                    .setCancelButton()
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), requestCode)
                    }
                    .show()
            } else requestPermissions(arrayOf(permission), requestCode)
        } else if (alsoCheckAlarmPermission)
            checkAlarmPermission(alarmPermissionResultLauncher, onSuccess)
        else onSuccess()
    } else if (alsoCheckAlarmPermission)
        checkAlarmPermission(alarmPermissionResultLauncher, onSuccess)
    else onSuccess()
}

fun Activity.checkAlarmPermission(
    resultLauncher: ActivityResultLauncher<Intent>?,
    onSuccess: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (getSystemService<AlarmManager>()!!.canScheduleExactAlarms()) {
            onSuccess()
        } else {
            MaterialAlertDialogBuilder(this)
                .setMessage(R.string.please_grant_notally_alarm)
                .setCancelButton()
                .setPositiveButton(R.string.continue_) { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.data = Uri.parse("package:$packageName")
                    if (resultLauncher != null) {
                        resultLauncher.launch(intent)
                    } else {
                        startActivity(intent)
                    }
                }
                .show()
        }
    } else onSuccess()
}

fun Fragment.displayEditLabelDialog(
    oldValue: String,
    model: BaseNoteModel,
    onUpdateLabel: ((oldLabel: String, newLabel: String) -> Unit)? = null,
) {
    requireContext().displayEditLabelDialog(oldValue, model, layoutInflater, onUpdateLabel)
}

fun Activity.displayEditLabelDialog(
    oldValue: String,
    model: BaseNoteModel,
    onUpdateLabel: ((oldLabel: String, newLabel: String) -> Unit)? = null,
) {
    displayEditLabelDialog(oldValue, model, layoutInflater, onUpdateLabel)
}

fun Context.displayEditLabelDialog(
    oldValue: String,
    model: BaseNoteModel,
    layoutInflater: LayoutInflater,
    onUpdateLabel: ((oldLabel: String, newLabel: String) -> Unit)? = null,
) {
    val dialogBinding = DialogInputBinding.inflate(layoutInflater)
    dialogBinding.EditText.setText(oldValue)
    MaterialAlertDialogBuilder(this)
        .setView(dialogBinding.root)
        .setTitle(R.string.edit_label)
        .setCancelButton()
        .setPositiveButton(R.string.save) { dialog, _ ->
            val value = dialogBinding.EditText.text.toString().trim()
            if (value.isNotEmpty()) {
                model.updateLabel(oldValue, value) { success ->
                    if (success) {
                        onUpdateLabel?.invoke(oldValue, value)
                        dialog.dismiss()
                    } else showToast(R.string.label_exists)
                }
            }
        }
        .showAndFocus(dialogBinding.EditText, allowFullSize = true) { positiveButton ->
            dialogBinding.EditText.doAfterTextChanged { text ->
                positiveButton.isEnabled = !text.isNullOrEmpty()
            }
            positiveButton.isEnabled = oldValue.isNotEmpty()
        }
}

private fun formatTimestamp(timestamp: Long, dateFormat: DateFormat): String {
    val date = Date(timestamp)
    return when (dateFormat) {
        DateFormat.RELATIVE -> PrettyTime().format(date)
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(date)
    }
}

fun MaterialAlertDialogBuilder.showAndFocus(
    viewToFocus: View? = null,
    selectAll: Boolean = false,
    allowFullSize: Boolean = false,
    onShowListener: DialogInterface.OnShowListener? = null,
    applyToPositiveButton: ((positiveButton: Button) -> Unit)? = null,
): AlertDialog {
    if (allowFullSize) {
        setBackgroundInsetEnd(0)
        setBackgroundInsetStart(0)
        setBackgroundInsetBottom(0)
        setBackgroundInsetTop(0)
    }
    return create().apply {
        viewToFocus?.requestFocus()
        if (viewToFocus is EditText) {
            if (selectAll) {
                viewToFocus.selectAll()
            }
            window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE)
        }
        if (allowFullSize) {
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
        }
        onShowListener?.let { setOnShowListener(it) }
        show()
        applyToPositiveButton?.let {
            getButton(AlertDialog.BUTTON_POSITIVE)?.let { positiveButton -> it(positiveButton) }
        }
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

fun Context.getColorFromAttr(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    val resolved = theme.resolveAttribute(attr, typedValue, true)
    if (!resolved) {
        throw IllegalArgumentException("Attribute not found in current theme")
    }
    return if (typedValue.resourceId != 0) {
        // It's a reference (@color/something), resolve it properly
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        // It's a direct color value
        typedValue.data
    }
}

fun View.setControlsContrastColorForAllViews(
    @ColorInt backgroundColor: Int,
    overwriteBackground: Boolean = true,
) {
    val controlsColor = context.getContrastFontColor(backgroundColor)
    setControlsColorForAllViews(controlsColor, backgroundColor, overwriteBackground)
}

var counter = 0

fun View.setControlsColorForAllViews(
    @ColorInt controlsColor: Int,
    @ColorInt backgroundColor: Int,
    overwriteBackground: Boolean = true,
) {
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.setControlsColorForAllViews(
                controlsColor,
                backgroundColor,
                overwriteBackground,
            ) // Recursive call for nested layouts
        }
        if (this is MaterialCardView) {
            checkedIconTint = ColorStateList.valueOf(controlsColor)
            val colorStateList =
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(controlsColor, controlsColor.withAlpha(0.3f)),
                )
            setStrokeColor(colorStateList)
        }
    } else {
        val controlsStateList =
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled),
                ),
                intArrayOf(controlsColor, controlsColor.withAlpha(0.3f)),
            )
        if (this is Chip) {
            setTextColor(controlsStateList)
            setLinkTextColor(controlsStateList)
            chipBackgroundColor = ColorStateList.valueOf(backgroundColor)
            chipIconTint = controlsStateList
            chipStrokeColor = controlsStateList
            return
        }
        if (this is TextView) {
            setCompoundDrawableTint(controlsColor)
            setTextColor(controlsStateList)
            setLinkTextColor(controlsStateList)
            if (isTextSelectable || this is EditText) {
                val highlight = controlsColor.withAlpha(0.4f)
                setHintTextColor(highlight)
                highlightColor = highlight
                setSelectionHandleColor(controlsColor.withAlpha(0.8f))
            }
        }
        if (this is CompoundButton) {
            buttonTintList = controlsStateList
        }
        if (this is MaterialButton) {
            iconTint = controlsStateList
        }
        if (this is ImageButton) {
            imageTintList = controlsStateList
        }
        if (this is MaterialCheckBox) {
            buttonIconTintList = ColorStateList.valueOf(backgroundColor)
        }
        if (overwriteBackground) {
            backgroundTintList = ColorStateList.valueOf(controlsColor)
        }
    }
}

fun TextView.setSelectionHandleColor(@ColorInt color: Int) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textSelectHandleLeft?.withTint(color)?.let { setTextSelectHandleLeft(it) }
            textSelectHandleRight?.withTint(color)?.let { setTextSelectHandleRight(it) }
            textSelectHandle?.withTint(color)?.let { setTextSelectHandle(it) }
            textCursorDrawable?.let { DrawableCompat.setTint(it, color) }
        } else {
            setSelectHandleColor(color)
        }
    } catch (_: Exception) {}
}

/**
 * This uses light-graylisted Android APIs. Verified that it works in devices running APIs 21
 * and 28. Source: https://gist.github.com/carranca/7e3414622ad7fc6ef375c8cd8dc840c9
 */
private fun TextView.setSelectHandleColor(@ColorInt color: Int) {
    // Retrieve a reference to this text field's android.widget.Editor
    val editor = getEditor()

    handles.forEach {
        // Retrieve the field pointing to the drawable currently being used for the select handle
        val resourceField = TextView::class.java.getDeclaredField(it.resourceFieldName)
        resourceField.isAccessible = true

        // Retrieve the drawable resource from that field
        val drawableId = resourceField.getInt(this)
        val drawable = ContextCompat.getDrawable(context, drawableId)

        // Apply a filter on that drawable with the desired colour
        drawable?.setColorFilter(color, PorterDuff.Mode.SRC_IN)

        // Override the drawable being used by the Editor with our coloured drawable
        val selectHandleField = editor.javaClass.getDeclaredField(it.selectHandleFieldName)
        selectHandleField.isAccessible = true
        selectHandleField.set(editor, drawable)
    }
}

private class HandleDescriptor(val resourceFieldName: String, val selectHandleFieldName: String)

private val handles =
    arrayOf(
        HandleDescriptor(
            resourceFieldName = "mTextSelectHandleRes",
            selectHandleFieldName = "mSelectHandleCenter",
        ),
        HandleDescriptor(
            resourceFieldName = "mTextSelectHandleLeftRes",
            selectHandleFieldName = "mSelectHandleLeft",
        ),
        HandleDescriptor(
            resourceFieldName = "mTextSelectHandleRightRes",
            selectHandleFieldName = "mSelectHandleRight",
        ),
    )

private fun TextView.getEditor(): Any {
    val editorField = TextView::class.java.getDeclaredField("mEditor")
    editorField.isAccessible = true
    return editorField.get(this)
}

fun TextView.setCompoundDrawableTint(@ColorInt color: Int) {
    compoundDrawablesRelative.forEach { drawable ->
        drawable?.let { DrawableCompat.setTint(DrawableCompat.wrap(it), color) }
    }
    compoundDrawables.forEach { drawable ->
        drawable?.let { DrawableCompat.setTint(DrawableCompat.wrap(it), color) }
    }
    (background as? MaterialShapeDrawable)?.setStrokeTint(color)
}

fun Drawable.withTint(@ColorInt color: Int): Drawable {
    val oldDrawable = DrawableCompat.unwrap<Drawable>(this)
    val newDrawable = oldDrawable.constantState?.newDrawable()?.mutate() ?: oldDrawable.mutate()
    val wrappedDrawable = DrawableCompat.wrap(newDrawable)
    DrawableCompat.setTint(wrappedDrawable, color)
    return wrappedDrawable
}

@ColorInt
fun Context.getContrastFontColor(@ColorInt backgroundColor: Int): Int {
    return if (backgroundColor.isLightColor()) ContextCompat.getColor(this, R.color.TextDark)
    else ContextCompat.getColor(this, R.color.TextLight)
}

fun @receiver:ColorInt Int.isLightColor() = ColorUtils.calculateLuminance(this) > 0.5

fun MaterialAlertDialogBuilder.setCancelButton(listener: DialogInterface.OnClickListener? = null) =
    setNegativeButton(R.string.cancel, listener)

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
        .setCancelButton()
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

fun Context.restartApplication(
    fragmentIdToOpen: Int? = null,
    extra: Pair<String, Boolean>? = null,
) {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent!!.component
    val mainIntent =
        Intent.makeRestartActivityTask(componentName).apply {
            fragmentIdToOpen?.let { putExtra(MainActivity.EXTRA_FRAGMENT_TO_OPEN, it) }
            extra?.let { (key, value) -> putExtra(key, value) }
        }
    mainIntent.setPackage(packageName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}

@ColorInt
fun Context.extractColor(color: String): Int {
    return when (color) {
        BaseNote.COLOR_DEFAULT -> return getColorFromAttr(R.attr.colorSurface)
        else -> android.graphics.Color.parseColor(color)
    }
}

fun ViewGroup.addFastScroll(context: Context) {
    FastScrollerBuilder(this)
        .useMd2Style()
        .setTrackDrawable(ContextCompat.getDrawable(context, R.drawable.scroll_track)!!)
        .setPadding(0, 0, 2.dp, 0)
        .build()
}

fun Window.setLightStatusAndNavBar(value: Boolean, view: View = decorView) {
    val windowInsetsControllerCompat = WindowInsetsControllerCompat(this, view)
    windowInsetsControllerCompat.isAppearanceLightStatusBars = value
    windowInsetsControllerCompat.isAppearanceLightNavigationBars = value
}

fun ChipGroup.bindLabels(
    labels: List<String>,
    textSize: TextSize,
    paddingTop: Boolean,
    color: Int? = null,
    onClick: ((label: String) -> Unit)? = null,
    onLongClick: ((label: String) -> Unit)? = null,
) {
    if (labels.isEmpty()) {
        visibility = View.GONE
    } else {
        apply {
            visibility = View.VISIBLE
            removeAllViews()
            updatePadding(top = if (paddingTop) 8.dp else 0)
        }
        val inflater = LayoutInflater.from(context)
        val labelSize = textSize.displayBodySize
        for (label in labels) {
            LabelBinding.inflate(inflater, this, true).root.apply {
                background = getOutlinedDrawable(this@bindLabels.context)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSize)
                text = label
                color?.let { setControlsContrastColorForAllViews(it) }
                onClick?.let { setOnClickListener { it(label) } }
                onLongClick?.let {
                    setOnLongClickListener {
                        it(label)
                        true
                    }
                }
            }
        }
    }
}

private fun getOutlinedDrawable(context: Context): MaterialShapeDrawable {
    val model =
        ShapeAppearanceModel.builder()
            .setAllCorners(RoundedCornerTreatment())
            .setAllCornerSizes(RelativeCornerSize(0.5f))
            .build()

    return MaterialShapeDrawable(model).apply {
        fillColor = ColorStateList.valueOf(0)
        strokeWidth = context.resources.displayMetrics.density
        strokeColor = ContextCompat.getColorStateList(context, R.color.chip_stroke)
    }
}

fun RecyclerView.initListView(context: Context) {
    setHasFixedSize(true)
    layoutManager = LinearLayoutManager(context)
    addItemDecoration(DividerItemDecoration(context, RecyclerView.VERTICAL))
    setPadding(0, 0, 0, 0)
}

val RecyclerView.focusedViewHolder
    get() =
        focusedChild?.let { view ->
            val position = getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                null
            } else {
                findViewHolderForAdapterPosition(position)
            }
        }

fun RecyclerView.showKeyboardOnFocusedItem() {
    (focusedViewHolder as? ListItemVH)?.let {
        it.binding.root.context?.showKeyboard(it.binding.EditText)
    }
}

fun RecyclerView.hideKeyboardOnFocusedItem() {
    (focusedViewHolder as? ListItemVH)?.let {
        it.binding.root.context?.hideKeyboard(it.binding.EditText)
    }
}

fun MaterialAutoCompleteTextView.select(value: CharSequence) {
    setText(value, false)
}

fun Context.createTextView(textResId: Int, padding: Int = 16.dp): TextView {
    return AppCompatTextView(this).apply {
        setText(textResId)
        TextViewCompat.setTextAppearance(
            this,
            android.R.style.TextAppearance_Material_DialogWindowTitle,
        )
        updatePadding(padding, padding, padding, padding)
        maxLines = Integer.MAX_VALUE
        ellipsize = null
    }
}
