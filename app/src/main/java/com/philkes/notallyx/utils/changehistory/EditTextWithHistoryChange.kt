package com.philkes.notallyx.utils.changehistory

import android.text.Editable
import androidx.core.text.getSpans
import com.philkes.notallyx.presentation.clone
import com.philkes.notallyx.presentation.view.misc.HighlightSpan
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class EditTextWithHistoryChange(
    private val editText: StylableEditTextWithHistory,
    before: EditTextState,
    after: EditTextState,
    private val updateModel: (newValue: Editable) -> Unit,
) : ValueChange<EditTextState>(before, after) {

    override fun update(value: EditTextState, isUndo: Boolean) {
        editText.applyWithoutTextWatcher {
            val text = value.text.withoutSpans<HighlightSpan>()
            setText(text)
            updateModel.invoke(text)
            requestFocus()
            setSelection(value.cursorPos)
        }
    }
}

data class EditTextState(val text: Editable, val cursorPos: Int)

inline fun <reified T : Any> Editable.withoutSpans(): Editable =
    clone().apply { this.getSpans<T>().forEach { removeSpan(it) } }
