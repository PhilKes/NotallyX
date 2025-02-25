package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.ListState

class ChangeCheckedForAllChange(
    old: ListState,
    new: ListState,
    private val listManager: ListManager,
) : ValueChange<ListState>(new, old) {

    override fun update(value: ListState, isUndo: Boolean) {
        // Since delete checked Changes can be quite complex simply use snapshots before/after
        listManager.setItems(if (isUndo) oldValue else newValue)
    }

    override fun toString(): String {
        return "ChangeCheckedForAllChange"
    }
}
