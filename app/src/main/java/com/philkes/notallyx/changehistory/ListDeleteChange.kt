package com.philkes.notallyx.changehistory

import com.philkes.notallyx.model.ListItem
import com.philkes.notallyx.recyclerview.ListManager

class ListDeleteChange(
    internal val itemOrder: Int,
    internal val deletedItem: ListItem,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.deleteById(deletedItem.id, pushChange = false)
    }

    override fun undo() {
        listManager.add(itemOrder, deletedItem, pushChange = false)
    }

    override fun toString(): String {
        return "DeleteChange id: ${deletedItem.id} itemOrder: $itemOrder deletedItem: $deletedItem"
    }
}
