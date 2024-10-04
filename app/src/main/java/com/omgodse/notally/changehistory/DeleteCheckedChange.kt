package com.omgodse.notally.changehistory

import com.omgodse.notally.model.ListItem
import com.omgodse.notally.model.toReadableString
import com.omgodse.notally.recyclerview.ListManager

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
