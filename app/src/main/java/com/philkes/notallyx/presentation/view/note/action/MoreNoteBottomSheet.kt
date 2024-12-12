package com.philkes.notallyx.presentation.view.note.action

import com.philkes.notallyx.R

/** BottomSheet inside list-note for all common note actions. */
class MoreNoteBottomSheet(
    callbacks: MoreActions,
    additionalActions: Collection<Action> = listOf(),
) : ActionBottomSheet(createActions(callbacks, additionalActions)) {

    companion object {
        const val TAG = "MoreNoteBottomSheet"

        internal fun createActions(callbacks: MoreActions, additionalActions: Collection<Action>) =
            listOf(
                Action(R.string.change_color, R.drawable.change_color) { callbacks.changeColor() },
                Action(R.string.labels, R.drawable.label) { callbacks.changeLabels() },
            ) + additionalActions
    }
}

interface MoreActions {
    fun changeColor()

    fun changeLabels()
}
