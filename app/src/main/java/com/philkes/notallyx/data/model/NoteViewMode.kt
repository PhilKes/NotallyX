package com.philkes.notallyx.data.model

import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.viewmodel.preference.StaticTextProvider

enum class NoteViewMode(override val textResId: Int) : StaticTextProvider {
    READ_ONLY(R.string.read_only),
    EDIT(R.string.edit);

    companion object {
        fun valueOfOrDefault(value: String) =
            try {
                NoteViewMode.valueOf(value)
            } catch (e: Exception) {
                EDIT
            }
    }
}
