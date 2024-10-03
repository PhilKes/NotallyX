package com.omgodse.notally.recyclerview

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.omgodse.notally.room.ListItem

class ListItemNoSortCallback(adapter: RecyclerView.Adapter<*>?) :
    SortedListAdapterCallback<ListItem>(adapter) {

    override fun compare(item1: ListItem?, item2: ListItem?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            else -> item1!!.sortingPosition!!.compareTo(item2!!.sortingPosition!!)
        }
    }

    override fun areContentsTheSame(oldItem: ListItem?, newItem: ListItem?): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(item1: ListItem?, item2: ListItem?): Boolean {
        return item1?.id == item2?.id
    }
}
