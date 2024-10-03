package com.omgodse.notally.changehistory

import com.omgodse.notally.recyclerview.ListManager
import com.omgodse.notally.recyclerview.toReadableString
import com.omgodse.notally.room.ListItem

class DeleteCheckedChange(
    internal val itemsBeforeDelete: MutableList<ListItem>,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.deleteCheckedItems(pushChange = false)
    }

    override fun undo() {
        listManager.updateList(itemsBeforeDelete)
    }

    override fun toString(): String {
        return "DeleteCheckedChange itemsBeforeDelete:\n${itemsBeforeDelete.toReadableString()}"
    }
}
