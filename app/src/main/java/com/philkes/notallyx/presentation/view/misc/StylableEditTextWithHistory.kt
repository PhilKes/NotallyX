package com.philkes.notallyx.presentation.view.misc

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.LayoutInflater
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.databinding.DialogTextInput2Binding
import com.philkes.notallyx.presentation.clone
import com.philkes.notallyx.presentation.createTextWatcherWithHistory
import com.philkes.notallyx.presentation.removeSelectionFromSpans
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange
import com.philkes.notallyx.utils.findWebUrls
import com.philkes.notallyx.utils.getLatestText
import com.philkes.notallyx.utils.isWebUrl

/**
 * [AppCompatEditText] whose changes (text edits or span changes) are pushed to [changeHistory].
 * *
 */
class StylableEditTextWithHistory(context: Context, attrs: AttributeSet) :
    HighlightableEditText(context, attrs) {

    var isActionModeOn = false
    private var changeHistory: ChangeHistory? = null
    private var updateModel: ((text: Editable) -> Unit)? = null

    /**
     * If this is called every future text or span change is pushed to [changeHistory].
     *
     * @param updateModel Function that is called when undo/redo of [changeHistory] is triggered *
     */
    fun initHistory(changeHistory: ChangeHistory, updateModel: (text: Editable) -> Unit) {
        this.textWatcher?.let { removeTextChangedListener(it) }
        this.changeHistory = changeHistory
        this.updateModel = updateModel
        this.textWatcher =
            createTextWatcherWithHistory(
                changeHistory,
                { text, start, count ->
                    clearHighlights()
                    if (count > 1) {
                        val changedText = text.substring(start, start + count)
                        changedText.findWebUrls().forEach { (urlStart, urlEnd) ->
                            super.getText()
                                ?.setSpan(
                                    URLSpan(changedText.substring(urlStart, urlEnd)),
                                    start + urlStart,
                                    start + urlEnd,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                )
                        }
                    }
                },
            ) { text: Editable ->
                updateModel(text.clone())
            }
        this.textWatcher?.let { addTextChangedListener(it) }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!isActionModeOn) {
            super.onWindowFocusChanged(hasWindowFocus)
        }
    }

    fun getSpanText(span: CharacterStyle): String {
        val (spanStart, spanEnd) = getSpanRange(span)
        return super.getText()!!.substring(spanStart, spanEnd)
    }

    fun getSelectionText(): String? {
        if (selectionStart == -1 || selectionEnd == -1) {
            return null
        }
        return super.getText()!!.substring(selectionStart, selectionEnd)
    }

    fun getSpans(start: Int = selectionStart, end: Int = selectionEnd): Collection<CharacterStyle> {
        return super.getText()?.getSpans(start, end, CharacterStyle::class.java)?.toList()
            ?: listOf()
    }

    fun getSpans(
        start: Int = selectionStart,
        end: Int = selectionEnd,
        type: TextStyleType,
    ): Collection<CharacterStyle> {
        val spans = getSpans(start, end)
        return when (type) {
            TextStyleType.LINK -> spans.filterIsInstance<URLSpan>()
            TextStyleType.BOLD -> spans.filter { it is StyleSpan && it.style == Typeface.BOLD }
            TextStyleType.ITALIC -> spans.filter { it is StyleSpan && it.style == Typeface.ITALIC }
            TextStyleType.MONOSPACE ->
                spans.filter { it is TypefaceSpan && it.family == "monospace" }
            TextStyleType.STRIKETHROUGH -> spans.filterIsInstance<StrikethroughSpan>()
        }
    }

    fun clearFormatting(
        start: Int = selectionStart,
        end: Int = selectionEnd,
        type: TextStyleType? = null,
    ) {
        changeTextWithHistory { text ->
            if (type == null) {
                text.removeSelectionFromSpans(start, end)
            } else {
                val spans: Collection<CharacterStyle> = getSpans(start, end, type)
                text.removeSelectionFromSpans(start, end, spans)
            }
        }
    }

    enum class TextStyleType {
        LINK,
        BOLD,
        ITALIC,
        MONOSPACE,
        STRIKETHROUGH,
    }

    /**
     * Removes [span] from `text`.
     *
     * @param removeText if this is `true` the text of the [span] is removed from `text`.
     */
    fun removeSpanWithHistory(
        span: CharacterStyle,
        removeText: Boolean = false,
        pushChange: Boolean = true,
    ) {
        val (start, end) = getSpanRange(span)
        val callback: (text: Editable) -> Unit = { text ->
            text.removeSelectionFromSpans(start, end)
            if (removeText) {
                text.delete(start, end)
            }
        }
        if (pushChange) {
            changeTextWithHistory(callback)
        } else {
            changeText(callback)
        }
    }

    fun addSpan(spanText: String, span: CharacterStyle, position: Int = selectionStart) {
        changeTextWithHistory { text ->
            text.insert(position, spanText)
            text.setSpan(
                span,
                position,
                position + spanText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    fun addSpans(
        spanText: String,
        spans: Collection<CharacterStyle>,
        position: Int = selectionStart,
        pushChange: Boolean = true,
    ) {
        val actualPosition =
            if (position < 0 || !hasFocus()) {
                text?.lastIndex?.let {
                    if (it > -1) {
                        it
                    } else 0
                } ?: 0
            } else position

        val callback: (text: Editable) -> Unit = { text ->
            text.insert(actualPosition, spanText)
            spans.forEach { span ->
                text.setSpan(
                    span,
                    actualPosition,
                    actualPosition + spanText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        if (pushChange) {
            changeTextWithHistory(callback)
        } else {
            changeText(callback)
        }
    }

    /**
     * Replaces [oldSpan] with [newSpan].
     *
     * @param spanText if this is not `null`, the spans text is also updated,
     */
    fun updateSpan(oldSpan: CharacterStyle, newSpan: CharacterStyle, spanText: String?) {
        val (oldSpanStart, oldSpanEnd) = getSpanRange(oldSpan)
        changeTextWithHistory { text ->
            text.removeSpan(oldSpan)
            if (spanText != null) {
                text.replace(oldSpanStart, oldSpanEnd, spanText)
                text.setSpan(
                    newSpan,
                    oldSpanStart,
                    oldSpanStart + spanText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                text.setSpan(newSpan, oldSpanStart, oldSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun applySpan(
        span: CharacterStyle,
        start: Int = selectionStart,
        end: Int = selectionEnd,
        pushChange: Boolean = true,
    ) {
        if (pushChange) {
            changeTextWithHistory { text ->
                text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            changeText { text -> text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
    }

    /**
     * Can be used to change `text` with according [EditTextWithHistoryChange] pushed automatically
     * to [changeHistory]. This method is used by all other members functions.
     */
    fun changeTextWithHistory(callback: (text: Editable) -> Unit) {
        val cursorPosBefore = selectionStart
        val (textBefore, textAfter) = changeText(callback)
        val textAfterClone = textAfter.clone()
        updateModel?.invoke(textAfterClone)
        changeHistory?.push(
            EditTextWithHistoryChange(
                this,
                EditTextState(textBefore.clone(), cursorPosBefore),
                EditTextState(textAfterClone, selectionStart),
            ) { text ->
                updateModel?.invoke(text.clone())
            }
        )
    }

    internal fun showEditDialog(
        urlSpan: URLSpan,
        isNewUnnamedLink: Boolean = false,
        onClose: (() -> Unit)? = null,
    ) {
        val displayTextBefore = getSpanText(urlSpan)
        showLinkDialog(context, urlSpan.url, displayTextBefore, isNewUnnamedLink, onClose) {
            urlAfter,
            displayTextAfter ->
            if (urlAfter != null) {
                updateSpan(
                    urlSpan,
                    URLSpan(urlAfter),
                    if (displayTextAfter == displayTextBefore) null else displayTextAfter,
                )
            } else {
                removeSpanWithHistory(
                    urlSpan,
                    removeText = isNewUnnamedLink,
                    pushChange = !isNewUnnamedLink,
                )
            }
        }
    }

    internal fun showAddLinkDialog(
        context: Context,
        isNewUnnamedLink: Boolean = false,
        mode: ActionMode? = null,
        presetUrl: String? = null,
        presetDisplayText: String? = null,
        onClose: (() -> Unit)? = null,
    ) {
        val displayedUrl: String =
            presetUrl
                ?: ContextCompat.getSystemService(this.context, ClipboardManager::class.java)
                    ?.getLatestText()
                    ?.let { if (it.isWebUrl()) it.toString() else "" }
                ?: ""
        val displayTextBefore = presetDisplayText ?: getSelectionText() ?: ""
        showLinkDialog(
            context,
            displayedUrl,
            displayTextBefore,
            isNewUnnamedLink,
            onClose = onClose,
        ) { urlAfter, displayTextAfter ->
            if (urlAfter == null) {
                return@showLinkDialog
            }
            this.changeTextWithHistory { text ->
                val start = this.selectionStart
                text.replace(start, this.selectionEnd, displayTextAfter)
                text.setSpan(
                    URLSpan(urlAfter),
                    start,
                    start + displayTextAfter.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            mode?.finish()
        }
    }

    private fun showLinkDialog(
        context: Context,
        urlBefore: String,
        displayTextBefore: String,
        isNewUnnamedLink: Boolean,
        onClose: (() -> Unit)? = null,
        onSuccess: (urlAfter: String?, displayTextAfter: String) -> Unit,
    ) {
        val isNoteUrl = urlBefore.isNoteUrl()
        val layout =
            DialogTextInput2Binding.inflate(LayoutInflater.from(context)).apply {
                InputText1.setText(displayTextBefore)
                InputTextLayout1.setHint(R.string.display_text)
                InputText2.setText(urlBefore)
                if (isNoteUrl) {
                    InputTextLayout2.visibility = GONE
                } else {
                    InputTextLayout2.setHint(R.string.link)
                }
            }

        MaterialAlertDialogBuilder(context)
            .setView(layout.root)
            .setTitle(R.string.edit_link)
            .setPositiveButton(R.string.save) { _, _ ->
                val displayTextAfter = layout.InputText1.text.toString()
                val urlAfter = layout.InputText2.text.toString()
                onSuccess.invoke(urlAfter, displayTextAfter)
                onClose?.invoke()
            }
            .setCancelButton { dialog, _ ->
                dialog.cancel()
                onClose?.invoke()
            }
            .setNeutralButton(R.string.clear) { dialog, _ ->
                dialog.cancel()
                onSuccess.invoke(null, displayTextBefore)
                onClose?.invoke()
            }
            .showAndFocus(
                viewToFocus = if (isNoteUrl) layout.InputText1 else layout.InputText2,
                selectAll = isNewUnnamedLink,
                fullWidth = true,
            )
    }
}
