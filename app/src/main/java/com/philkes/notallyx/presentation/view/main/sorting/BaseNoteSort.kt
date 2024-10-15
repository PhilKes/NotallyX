package com.philkes.notallyx.presentation.view.main.sorting

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.presentation.view.misc.SortDirection

abstract class BaseNoteSort(
    adapter: RecyclerView.Adapter<*>?,
    private val sortDirection: SortDirection,
) : SortedListAdapterCallback<Item>(adapter) {

    abstract fun compare(note1: BaseNote, note2: BaseNote, sortDirection: SortDirection): Int

    override fun compare(item1: Item?, item2: Item?): Int {
        return when {
            item1 == null && item2 == null -> 0
            item1 == null && item2 != null -> -1
            item1 != null && item2 == null -> 1
            item1 is BaseNote && item2 is BaseNote -> compare(item1, item2, sortDirection)
            item1 is Header && item2 is Header -> 0
            else -> 0
        }
    }

    override fun areContentsTheSame(oldItem: Item?, newItem: Item?): Boolean {
        return oldItem == newItem
    }

    override fun areItemsTheSame(item1: Item?, item2: Item?): Boolean {
        return when {
            item1 is BaseNote && item2 is BaseNote -> item1.id == item2.id
            item1 is Header && item2 is Header -> item1 == item2
            else -> false
        }
    }
}
