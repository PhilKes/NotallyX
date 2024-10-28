package com.philkes.notallyx.utils.changehistory

import android.text.Editable
import com.philkes.notallyx.presentation.view.misc.EditTextWithHistory

class EditTextWithHistoryChange(
    private val editText: EditTextWithHistory,
    textBefore: Editable,
    textAfter: Editable,
    private val updateModel: (newValue: Editable) -> Unit,
) : ValueChange<Editable>(textAfter, textBefore) {

    private val cursorPosition = editText.selectionStart

    override fun update(value: Editable, isUndo: Boolean) {
        editText.applyWithoutTextWatcher {
            text = value
            updateModel.invoke(value)
            requestFocus()
            setSelection(Math.min(value.length, cursorPosition + (if (isUndo) 1 else 0)))
        }
    }
}
