package com.omgodse.notally.recyclerview

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.omgodse.notally.room.ListItem

class ListItemSortedByCheckedCallback(adapter: RecyclerView.Adapter<*>?) :
    SortedListAdapterCallback<ListItem>(adapter) {

    internal lateinit var items: ListItemSortedList

    fun setList(items: ListItemSortedList) {
        this.items = items
    }

    // TODO: remove
    override fun compare(item1: ListItem?, item2: ListItem?): Int {
        val cmp = compareInternal(item1, item2)
        //        println("cmp: $cmp item1: $item1\t item2: $item2")
        return cmp
    }

    fun compareInternal(item1: ListItem?, item2: ListItem?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            item1!!.checked != item2!!.checked -> compareChecked(item1, item2)
            else -> item1.sortingPosition!!.compareTo(item2!!.sortingPosition!!)
        }
    }

    private fun compareChecked(item1: ListItem, item2: ListItem): Int {
        return when {
            item1.id == item2.id -> if (item1.checked) -1 else 1
            // if a parent is checked and the child has not been checked yet it should be sorted
            // under
            // the not yet checked child
            item1.isChild && !item2.isChild && item2.checked -> -1
            item1.isChild && !item2.isChild && !item2.checked -> 1

            //            item1.isChild && item2.isChild ->
            // item1.sortingPosition!!.compareTo(item2!!.sortingPosition!!)

            // if a parent is compared with a children compare the 2 parents instead
            item1!!.isChild && !item2!!.isChild -> {
                val parent1Info = items.findParent(item1)
                // if parent is being sorted it is not yet present in the list
                if (parent1Info == null || parent1Info.second.id == item2.id) {
                    return 1
                }
                return compare(parent1Info.second, item2)
            }

            !item1!!.isChild && item2!!.isChild -> {
                val parent2Info = items.findParent(item2)
                // if parent is being sorted it is not yet present in the list
                if (parent2Info == null || parent2Info.second.id == item1.id) {
                    return -1
                }
                return compare(item1, parent2Info.second)
            }
            item1.checked && !item2.checked -> 1
            !item1.checked && item2.checked -> -1
            else -> 0
        }
    }

    override fun areContentsTheSame(oldItem: ListItem?, newItem: ListItem?): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(item1: ListItem?, item2: ListItem?): Boolean {
        return item1?.id == item2?.id
    }

    override fun onChanged(position: Int, count: Int) {
        super.onChanged(position, count)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        super.onChanged(position, count, payload)
    }
}
