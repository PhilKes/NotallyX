package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

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
