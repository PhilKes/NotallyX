package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem

class SortedItemsList(val callback: ListItemParentSortCallback) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    init {
        this.callback.setItems(this)
    }
}
