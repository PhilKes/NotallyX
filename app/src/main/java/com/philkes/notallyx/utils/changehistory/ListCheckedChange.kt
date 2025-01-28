package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListManager

class ListCheckedChange(
    old: List<ListItem>,
    new: List<ListItem>,
    private val listManager: ListManager,
) : ValueChange<List<ListItem>>(new, old) {

    override fun update(value: List<ListItem>, isUndo: Boolean) {
        listManager.setItems(if (isUndo) oldValue else newValue, false)
    }

    override fun toString(): String {
        return "CheckedChange"
    }
}
