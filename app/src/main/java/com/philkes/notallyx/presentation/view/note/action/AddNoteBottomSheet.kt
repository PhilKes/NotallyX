package com.philkes.notallyx.presentation.view.note.action

import com.philkes.notallyx.R

/** BottomSheet inside text note for adding files, recording audio. */
class AddNoteBottomSheet(callbacks: AddNoteActions) : ActionBottomSheet(createActions(callbacks)) {

    companion object {
        const val TAG = "AddNoteBottomSheet"

        fun createActions(callbacks: AddNoteActions) =
            AddBottomSheet.createActions(callbacks) +
                listOf(Action(R.string.link_note, R.drawable.notebook) { callbacks.linkNote() })
    }
}

interface AddNoteActions : AddActions {
    fun linkNote()
}
