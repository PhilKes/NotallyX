package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.URLSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.data.model.isWebUrl
import com.philkes.notallyx.presentation.clone
import com.philkes.notallyx.presentation.createTextWatcherWithHistory
import com.philkes.notallyx.presentation.removeSelectionFromSpan
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange

/**
 * [AppCompatEditText] whose changes (text edits or span changes) are pushed to [changeHistory].
 * *
 */
class EditTextWithHistory(context: Context, attrs: AttributeSet) :
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
                    val changedText = text.substring(start, start + count)
                    if (changedText.isWebUrl() || changedText.isNoteUrl()) {
                        super.getText()
                            ?.setSpan(
                                URLSpan(changedText),
                                start,
                                start + count,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
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

    fun clearFormatting(start: Int = selectionStart, end: Int = selectionEnd) {
        changeTextWithHistory { text -> text.removeSelectionFromSpan(start, end) }
    }

    /**
     * Removes [span] from `text`.
     *
     * @param removeText if this is `true` the text of the [span] is removed from `text`.
     */
    fun removeSpan(span: CharacterStyle, removeText: Boolean = false, pushChange: Boolean = true) {
        val (start, end) = getSpanRange(span)
        val callback: (text: Editable) -> Unit = { text ->
            text.removeSelectionFromSpan(start, end)
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
}
