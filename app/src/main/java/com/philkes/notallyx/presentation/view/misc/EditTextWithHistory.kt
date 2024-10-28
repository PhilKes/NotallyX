package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
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
import com.philkes.notallyx.utils.changehistory.EditTextWithHistoryChange

/**
 * [AppCompatEditText] whose changes (text edits or span changes) are pushed to [changeHistory].
 * *
 */
class EditTextWithHistory(context: Context, attrs: AttributeSet) :
    AppCompatEditText(context, attrs) {

    var isActionModeOn = false
    private var changeHistory: ChangeHistory? = null
    private var updateModel: ((text: Editable) -> Unit)? = null
    private var textWatcher: TextWatcher? = null

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
                    val changedText = text.substring(start, start + count)
                    if (changedText.isWebUrl() || changedText.isNoteUrl()) {
                        this.text?.setSpan(
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

    @Deprecated(
        "You should not access text Editable directly, use other member functions to edit/read text properties.",
        replaceWith = ReplaceWith("changeText/applyWithoutTextWatcher/..."),
    )
    override fun getText(): Editable? {
        return super.getText()
    }

    fun getTextClone(): Editable {
        return super.getText()!!.clone()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        applyWithoutTextWatcher { super.setText(text, type) }
    }

    fun applyWithoutTextWatcher(
        callback: EditTextWithHistory.() -> Unit
    ): Pair<Editable, Editable> {
        val textBefore = super.getText()!!.clone()
        val editTextWatcher = textWatcher
        editTextWatcher?.let { removeTextChangedListener(it) }
        callback()
        editTextWatcher?.let { addTextChangedListener(it) }
        return Pair(textBefore, super.getText()!!.clone())
    }

    fun getSpanRange(span: CharacterStyle): Pair<Int, Int> {
        val text = super.getText()!!
        return Pair(text.getSpanStart(span), text.getSpanEnd(span))
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
    fun removeSpan(span: CharacterStyle, removeText: Boolean = false) {
        val (start, end) = getSpanRange(span)
        changeTextWithHistory { text ->
            text.removeSelectionFromSpan(start, end)
            if (removeText) {
                text.delete(start, end)
            }
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

    fun applySpan(span: CharacterStyle, start: Int = selectionStart, end: Int = selectionEnd) {
        changeTextWithHistory { text ->
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Can be used to change `text` with according [EditTextWithHistoryChange] pushed automatically
     * to [changeHistory]. This method is used by all other members functions.
     */
    fun changeTextWithHistory(callback: (text: Editable) -> Unit) {
        val (textBefore, textAfter) = changeText(callback)
        updateModel?.invoke(textAfter.clone())
        changeHistory?.push(
            EditTextWithHistoryChange(this, textBefore, textAfter) { text ->
                updateModel?.invoke(text.clone())
            }
        )
    }

    /**
     * Can be used to change `text` without triggering the [TextWatcher], which would push a
     * [EditTextWithHistoryChange] to [changeHistory].
     *
     * @return Clones of `text` before the changes and `text` after. *
     */
    fun changeText(callback: (text: Editable) -> Unit): Pair<Editable, Editable> {
        return applyWithoutTextWatcher { callback(super.getText()!!) }
    }
}
