package com.philkes.notallyx.changehistory

import com.philkes.notallyx.model.ListItem
import com.philkes.notallyx.model.toReadableString
import com.philkes.notallyx.recyclerview.ListManager

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
