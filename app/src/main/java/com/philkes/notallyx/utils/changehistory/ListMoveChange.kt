package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

class ListMoveChange(
    positionFrom: Int,
    internal var itemsBefore: List<ListItem>,
    internal var itemsAfter: List<ListItem>,
    internal val listManager: ListManager,
) : ListChange(positionFrom) {

    override fun redo() {
        // Moves are much more complex, therefore simply paste entire List like it was before
        listManager.setItems(itemsAfter)
    }

    override fun undo() {
        listManager.setItems(itemsBefore)
    }

    override fun toString(): String {
        return "MoveChange"
    }
}
