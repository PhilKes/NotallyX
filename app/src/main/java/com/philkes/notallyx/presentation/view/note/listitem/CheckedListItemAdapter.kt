package com.philkes.notallyx.presentation.view.note.listitem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import com.philkes.notallyx.utils.Cache.list

class CheckedListItemAdapter(
    @ColorInt var backgroundColor: Int,
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
    private val isCheckedListAdapter: Boolean,
) : RecyclerView.Adapter<ListItemVH>(), HighlightText {

    private lateinit var list: SortedList<ListItem>
    private val callback = ListItemDragCallback(elevation, listManager)
    private val touchHelper = ItemTouchHelper(callback)

    private val highlights = mutableMapOf<Int, MutableList<ListItemAdapter.ListItemHighlight>>()

    internal fun setList(list: SortedList<ListItem>) {
        this.list = list
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        val item = list[position]
        holder.bind(
            backgroundColor,
            item,
            position,
            highlights[position],
            preferences.listItemSorting.value,
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerListItemBinding.inflate(inflater, parent, false)
        binding.root.background = parent.background
        return ListItemVH(binding, listManager, touchHelper, textSize, isCheckedListAdapter)
    }

    internal fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        notifyDataSetChanged()
    }

    //    internal fun setList(list: ListItemSortedList) {
    //        this.list = list
    //    }

    internal fun clearHighlights(): Set<Int> {
        val highlightedItemPos =
            highlights.entries.flatMap { (_, value) -> value.map { it.itemPos } }.toSet()
        highlights.clear()
        highlightedItemPos.forEach { notifyItemChanged(it) }
        return highlightedItemPos
    }

    override fun highlightText(highlight: ListItemAdapter.ListItemHighlight) {
        if (highlights.containsKey(highlight.itemPos)) {
            highlights[highlight.itemPos]!!.add(highlight)
        } else {
            highlights[highlight.itemPos] = mutableListOf(highlight)
        }
        notifyItemChanged(highlight.itemPos)
    }

    internal fun selectHighlight(pos: Int): Int {
        var selectedItemPos = -1
        highlights.entries.forEach { (_, value) ->
            value.forEach {
                val isSelected = it.selected
                it.selected = it.resultPos == pos
                if (isSelected != it.selected) {
                    notifyItemChanged(it.itemPos)
                }
                if (it.selected) {
                    selectedItemPos = it.itemPos
                }
            }
        }
        return selectedItemPos
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ListItem>() {
                override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem) =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem) =
                    oldItem == newItem
            }
    }
}
