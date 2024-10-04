package com.omgodse.notally.changehistory

import com.omgodse.notally.recyclerview.ListManager
import com.omgodse.notally.room.ListItem

class ListDeleteChange(
    internal val sortingPosition: Int,
    internal val deletedItem: ListItem,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.deleteById(deletedItem.id, pushChange = false)
    }

    override fun undo() {
        listManager.add(sortingPosition, deletedItem, pushChange = false)
    }

    override fun toString(): String {
        return "DeleteChange id: ${deletedItem.id} sortingPosition: $sortingPosition deletedItem: $deletedItem"
    }
}
