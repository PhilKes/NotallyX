package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

class ChangeCheckedForAllChange(
    old: List<ListItem>,
    new: List<ListItem>,
    private val listManager: ListManager,
) : ValueChange<List<ListItem>>(new, old) {

    override fun update(value: List<ListItem>, isUndo: Boolean) {
        // Since delete checked Changes can be quite complex simply use snapshots before/after
        listManager.setItems(if (isUndo) oldValue else newValue)
    }

    override fun toString(): String {
        return "ChangeCheckedForAllChange"
    }
}
