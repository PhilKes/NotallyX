package com.philkes.notallyx.presentation.view.note.listitem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class ListItemAdapter(
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
) : RecyclerView.Adapter<ListItemVH>() {

    private lateinit var list: ListItemSortedList
    private val callback = ListItemDragCallback(elevation, listManager)
    private val touchHelper = ItemTouchHelper(callback)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemCount() = list.size()

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        val item = list[position]
        holder.bind(item, position, preferences.listItemSorting.value)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerListItemBinding.inflate(inflater, parent, false)
        binding.root.background = parent.background
        return ListItemVH(binding, listManager, touchHelper, textSize)
    }

    internal fun setList(list: ListItemSortedList) {
        this.list = list
    }
}
