package com.philkes.notallyx.presentation.view.note.action

import androidx.annotation.ColorInt
import com.philkes.notallyx.R

/** BottomSheet inside text note for adding files, recording audio. */
class AddNoteBottomSheet(callbacks: AddNoteActions, @ColorInt color: Int?) :
    ActionBottomSheet(createActions(callbacks), color) {

    companion object {
        const val TAG = "AddNoteBottomSheet"

        fun createActions(callbacks: AddNoteActions) =
            AddBottomSheet.createActions(callbacks) +
                listOf(
                    Action(R.string.link_note, R.drawable.notebook) { _ ->
                        callbacks.linkNote()
                        true
                    }
                )
    }
}

interface AddNoteActions : AddActions {
    fun linkNote()
}
