package com.philkes.notallyx.utils.changehistory

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class EditTextChange(
    private val editText: EditText,
    textBefore: Editable,
    textAfter: Editable,
    private val listener: TextWatcher,
    private val updateModel: (newValue: Editable) -> Unit,
) : ValueChange<Editable>(textAfter, textBefore) {

    private val cursorPosition = editText.selectionStart

    override fun update(value: Editable, isUndo: Boolean) {
        editText.removeTextChangedListener(listener)
        updateModel.invoke(value)
        editText.text = value
        editText.requestFocus()
        editText.setSelection(Math.min(value.length, cursorPosition + (if (isUndo) 1 else 0)))
        editText.addTextChangedListener(listener)
    }
}
