package com.philkes.notallyx.sorting

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.philkes.notallyx.model.ListItem

/** Sort algorithm that only sorts by [ListItem.order] */
class ListItemNoSortCallback(adapter: RecyclerView.Adapter<*>?) :
    SortedListAdapterCallback<ListItem>(adapter) {

    override fun compare(item1: ListItem?, item2: ListItem?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            else -> {
                val orderCmp = item1!!.order!!.compareTo(item2!!.order!!)
                if (orderCmp == 0 && item1.isChildOf(item2)) {
                    return -1 // happens when a parent with children is moved up, the children is
                    // moved first
                }
                if (orderCmp == 0 && item2.isChildOf(item1)) {
                    return 1 // happens when a parent with children is moved down, the children is
                    // moved first
                }
                return orderCmp
            }
        }
    }

    override fun areContentsTheSame(oldItem: ListItem?, newItem: ListItem?): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(item1: ListItem?, item2: ListItem?): Boolean {
        return item1?.id == item2?.id
    }
}
