package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.ListState

class ListMoveChange(old: ListState, new: ListState, internal val listManager: ListManager) :
    ValueChange<ListState>(new, old) {

    override fun update(value: ListState, isUndo: Boolean) {
        // Since move Changes can be quite complex simply use snapshots before/after
        listManager.setItems(if (isUndo) oldValue else newValue)
    }

    override fun toString(): String {
        return "MoveChange"
    }
}
