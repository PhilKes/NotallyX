package com.philkes.notallyx.presentation.view.note.action

import androidx.annotation.ColorInt
import com.philkes.notallyx.R

/** BottomSheet inside list-note for all common note actions. */
class MoreNoteBottomSheet(
    callbacks: MoreActions,
    additionalActions: Collection<Action> = listOf(),
    @ColorInt color: Int?,
) : ActionBottomSheet(createActions(callbacks, additionalActions), color) {

    companion object {
        const val TAG = "MoreNoteBottomSheet"

        internal fun createActions(callbacks: MoreActions, additionalActions: Collection<Action>) =
            listOf(
                Action(R.string.share, R.drawable.share) { callbacks.share() },
                Action(R.string.change_color, R.drawable.change_color) { callbacks.changeColor() },
                Action(R.string.reminders, R.drawable.notifications) {
                    callbacks.changeReminders()
                },
                Action(R.string.labels, R.drawable.label) { callbacks.changeLabels() },
            ) + additionalActions
    }
}

interface MoreActions {
    fun share()

    fun changeColor()

    fun changeReminders()

    fun changeLabels()
}
