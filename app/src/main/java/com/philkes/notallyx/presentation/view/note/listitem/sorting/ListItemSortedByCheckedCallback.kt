package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.containsId

/**
 * Sort algorithm that sorts items by [ListItem.checked] and [ListItem.order]. Children are always
 * sorted below their parents.
 */
class ListItemSortedByCheckedCallback(adapter: RecyclerView.Adapter<*>?) :
    SortedListCustomNotifyCallback<ListItem>(adapter) {

    internal lateinit var items: MutableList<ListItem>

    fun setList(items: MutableList<ListItem>) {
        this.items = items
    }

    private fun compareOrder(item1: ListItem, item2: ListItem) =
        item1.order!!.compareTo(item2.order!!)

    override fun compare(item1: ListItem?, item2: ListItem?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            //            item1 == item2 -> 0
            item1!!.id == item2!!.id -> 0 // TODO should return 0 ?
            item1.isDragged && !item2.isDragged || !item1.isDragged && item2.isDragged ->
                item1.order!!.compareTo(item2.order!!)
            item1.isChild && item2.isChild -> {
                val parent1 = items.findParent(item1)!!.second
                val parent2 = items.findParent(item2)!!.second
                return when {
                    parent1.id == parent2.id -> compareOrder(item1, item2)
                    else -> compare(parent1, parent2)
                }
            }

            !item1.isChild && item2.isChild -> {
                val parent2 =
                    if (item1.children.containsId(item2.id)) {
                        item1
                    } else {
                        items.findParent(item2)!!.second
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
                        items.findParent(item1)!!.second
                    }
                when {
                    item2.id == parent1.id -> compareOrder(item1, item2)
                    else -> compare(parent1, item2)
                }
            }

            //            else -> compareChecked(item1, item2)
            else -> compareOrder(item1, item2)
        }
    }

    private fun compareChecked(item1: ListItem, item2: ListItem): Int {
        return when {
            // if a parent gets checked and the child has not been checked yet the parent
            // should be sorted under the not yet checked child
            item1.isChild && !item2.isChild && item2.checked -> -1
            item1.isChild && !item2.isChild && !item2.checked -> 1

            item1.checked == item2.checked -> item1.order!!.compareTo(item2.order!!)
            item1.checked && !item2.checked -> 1
            !item1.checked && item2.checked -> -1
            else -> 0
        }
    }

    override fun areContentsTheSame(oldItem: ListItem?, newItem: ListItem?): Boolean {
        return oldItem?.equals(newItem) ?: false
    }

    override fun areItemsTheSame(item1: ListItem?, item2: ListItem?): Boolean {
        return item1?.id == item2?.id
    }
}
