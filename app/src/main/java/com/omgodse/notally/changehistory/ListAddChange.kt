package com.omgodse.notally.changehistory

import com.omgodse.notally.model.ListItem
import com.omgodse.notally.recyclerview.ListManager

class ListAddChange(
    position: Int,
    internal val deletedItemId: Int,
    internal val itemBeforeInsert: ListItem,
    private val listManager: ListManager,
) : ListChange(position) {
    override fun redo() {
        listManager.add(position, item = itemBeforeInsert, pushChange = false)
    }

    override fun undo() {
        listManager.deleteById(
            deletedItemId,
            childrenToDelete = itemBeforeInsert.children,
            pushChange = false,
        )
    }

    override fun toString(): String {
        return "Add at position: $position"
    }
}
