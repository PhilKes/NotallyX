package com.omgodse.notally.changehistory

import com.omgodse.notally.recyclerview.ListManager
import com.omgodse.notally.recyclerview.toReadableString
import com.omgodse.notally.room.ListItem

class DeleteCheckedChange(
    internal val deletedItems: List<ListItem>,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.deleteCheckedItems(pushChange = false)
    }

    override fun undo() {
        deletedItems.forEach { listManager.add(it.sortingPosition!!, it) }
    }

    override fun toString(): String {
        return "DeleteCheckedChange deletedItems:\n${deletedItems.toReadableString()}"
    }
}
