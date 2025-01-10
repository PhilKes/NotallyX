package com.philkes.notallyx.presentation.view.note.action

import com.philkes.notallyx.R

/** BottomSheet inside list-note for all common note actions and list-item actions. */
class MoreListBottomSheet(
    callbacks: MoreListActions,
    additionalActions: Collection<Action> = listOf(),
) : ActionBottomSheet(createActions(callbacks, additionalActions)) {

    companion object {
        const val TAG = "MoreListBottomSheet"

        private fun createActions(callbacks: MoreListActions, actions: Collection<Action>) =
            MoreNoteBottomSheet.createActions(callbacks, actions) +
                listOf(
                    Action(
                        R.string.delete_checked_items,
                        R.drawable.delete_all,
                        showDividerAbove = true,
                    ) {
                        callbacks.deleteChecked()
                    },
                    Action(R.string.check_all_items, R.drawable.checkbox_checked) {
                        callbacks.checkAll()
                    },
                    Action(R.string.uncheck_all_items, R.drawable.checkbox_unchecked) {
                        callbacks.uncheckAll()
                    },
                )
    }
}

interface MoreListActions : MoreActions {
    fun deleteChecked()

    fun checkAll()

    fun uncheckAll()
}
