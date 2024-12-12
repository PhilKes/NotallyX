package com.philkes.notallyx.utils.changehistory

import android.text.Editable
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class EditTextWithHistoryChange(
    private val editText: StylableEditTextWithHistory,
    before: EditTextState,
    after: EditTextState,
    private val updateModel: (newValue: Editable) -> Unit,
) : ValueChange<EditTextState>(after, before) {

    override fun update(value: EditTextState, isUndo: Boolean) {
        editText.applyWithoutTextWatcher {
            setText(value.text)
            updateModel.invoke(value.text)
            requestFocus()
            setSelection(value.cursorPos)
        }
    }
}

data class EditTextState(val text: Editable, val cursorPos: Int)
