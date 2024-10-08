package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

class EditTextAutoClearFocus(context: Context, attributeSet: AttributeSet) :
    AppCompatEditText(context, attributeSet) {

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            this.clearFocus()
        }
        return super.onKeyPreIme(keyCode, event)
    }
}
