package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.toReadableString
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

class DeleteCheckedChange(
    internal val deletedItems: List<ListItem>,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.deleteCheckedItems(pushChange = false)
    }

    override fun undo() {
        deletedItems.forEach { listManager.add(it.order!!, it) }
    }

    override fun toString(): String {
        return "DeleteCheckedChange deletedItems:\n${deletedItems.toReadableString()}"
    }
}
