package com.philkes.notallyx.presentation.view.note.listitem

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class ListItemAdapter(
    @ColorInt var backgroundColor: Int,
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
) : RecyclerView.Adapter<ListItemVH>() {

    private lateinit var list: ListItemSortedList
    private val callback = ListItemDragCallback(elevation, listManager)
    private val touchHelper = ItemTouchHelper(callback)

    private val highlights = mutableMapOf<Int, MutableList<ListItemHighlight>>()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        touchHelper.attachToRecyclerView(recyclerView)
    }

    override fun getItemCount() = list.size()

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        val item = list[position]
        holder.bind(
            backgroundColor,
            item,
            position,
            highlights.get(position),
            preferences.listItemSorting.value,
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListItemVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerListItemBinding.inflate(inflater, parent, false)
        binding.root.background = parent.background
        return ListItemVH(binding, listManager, touchHelper, textSize)
    }

    internal fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        notifyDataSetChanged()
    }

    internal fun setList(list: ListItemSortedList) {
        this.list = list
    }

    internal fun clearHighlights() : Set<Int> {
        val highlightedItemPos= highlights.entries.flatMap { (_, value) ->
            value.map {it.itemPos }
        }.toSet()
        highlights.clear()
        return highlightedItemPos
//        itemPos.forEach { notifyItemChanged(it) }
    }

    internal fun highlightText(highlight: ListItemHighlight) {
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

    data class ListItemHighlight(
        val itemPos: Int,
        val resultPos: Int,
        val startIdx: Int,
        val endIdx: Int,
        var selected: Boolean,
    )
}
