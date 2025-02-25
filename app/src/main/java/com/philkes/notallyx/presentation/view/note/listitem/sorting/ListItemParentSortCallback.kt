package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.containsId

/** Sort algorithm that only sorts by [ListItem.order] */
class ListItemParentSortCallback(adapter: RecyclerView.Adapter<*>?) :
    SortedListCustomNotifyCallback<ListItem>(adapter) {

    private var items: SortedList<ListItem>? = null

    internal fun setItems(items: SortedList<ListItem>) {
        this.items = items
    }

    override fun compare(item1: ListItem?, item2: ListItem?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            item1!!.id == item2!!.id -> 0
            !item1.isChild && item2.isChild -> {
                val parent2 =
                    if (item1.children.containsId(item2.id)) {
                        item1
                    } else {
                        items!!.findParent(item2)!!.second
                    }
                return when {
                    item1.id == parent2.id -> compareOrder(item1, item2)
                    else -> compare(item1, parent2)
                }
            }

            item1.isChild && !item2.isChild -> {
                val parent1 =
                    if (item2.children.containsId(item1.id)) {
                        item2
                    } else {
                        items!!.findParent(item1)!!.second
                    }
                when {
                    item2.id == parent1.id -> compareOrder(item1, item2)
                    else -> compare(parent1, item2)
                }
            }
            else -> {
                return compareOrder(item1, item2)
            }
        }
    }

    private fun compareOrder(item1: ListItem, item2: ListItem): Int {
        val orderCmp = item1.order!!.compareTo(item2.order!!)
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

    override fun areContentsTheSame(oldItem: ListItem?, newItem: ListItem?): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(item1: ListItem?, item2: ListItem?): Boolean {
        return item1?.id == item2?.id
    }
}
