package com.philkes.notallyx.utils.changehistory

import android.text.TextWatcher
import android.text.style.CharacterStyle
import android.widget.EditText
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

open class ListEditTextChange(
    private val editText: EditText,
    position: Int,
    before: EditTextState,
    after: EditTextState,
    private val listener: TextWatcher,
    private val listManager: ListManager,
) : ListPositionValueChange<EditTextState>(after, before, position) {

    override fun update(position: Int, value: EditTextState, isUndo: Boolean) {
        listManager.changeText(editText, listener, position, value, pushChange = false)
        editText.apply {
            removeTextChangedListener(listener)
            text = value.text.withoutSpans<CharacterStyle>()
            requestFocus()
            setSelection(value.cursorPos)
            addTextChangedListener(listener)
        }
    }
}
