package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.philkes.notallyx.presentation.clone

open class EditTextWithWatcher(context: Context, attrs: AttributeSet) :
    AppCompatEditText(context, attrs) {
    var textWatcher: TextWatcher? = null

    override fun setText(text: CharSequence?, type: BufferType?) {
        applyWithoutTextWatcher { super.setText(text, type) }
    }

    fun setText(text: Editable) {
        super.setText(text, BufferType.EDITABLE)
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

    fun applyWithoutTextWatcher(
        callback: EditTextWithWatcher.() -> Unit
    ): Pair<Editable, Editable> {
        val textBefore = super.getText()!!.clone()
        val editTextWatcher = textWatcher
        editTextWatcher?.let { removeTextChangedListener(it) }
        callback()
        editTextWatcher?.let { addTextChangedListener(it) }
        return Pair(textBefore, super.getText()!!.clone())
    }

    fun changeText(callback: (text: Editable) -> Unit): Pair<Editable, Editable> {
        return applyWithoutTextWatcher { callback(super.getText()!!) }
    }
}
