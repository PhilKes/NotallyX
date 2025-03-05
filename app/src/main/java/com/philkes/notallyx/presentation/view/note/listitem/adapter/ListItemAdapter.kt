package com.philkes.notallyx.presentation.view.note.listitem.adapter

import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.HighlightText
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class ListItemAdapter(
    @ColorInt var backgroundColor: Int,
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
    private val isCheckedListAdapter: Boolean,
    scrollView: NestedScrollView,
) : ListAdapter<ListItem, ListItemVH>(DIFF_CALLBACK), HighlightText {

    private val itemAdapterBase =
        object :
            ListItemAdapterBase(
                this,
                backgroundColor,
                textSize,
                elevation,
                preferences,
                listManager,
                isCheckedListAdapter,
                scrollView,
            ) {
            override fun getItem(position: Int): ListItem = this@ListItemAdapter.getItem(position)
        }

    lateinit var items: MutableList<ListItem>
        private set

    override fun submitList(list: MutableList<ListItem>?) {
        list?.let { items = it }
        super.submitList(list)
    }

    override fun submitList(list: MutableList<ListItem>?, commitCallback: Runnable?) {
        list?.let { items = it }
        super.submitList(list, commitCallback)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemAdapterBase.onAttachedToRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        itemAdapterBase.onBindViewHolder(holder, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        itemAdapterBase.onCreateViewHolder(parent, viewType)

    internal fun setBackgroundColor(@ColorInt color: Int) =
        itemAdapterBase.setBackgroundColor(color)

    internal fun clearHighlights() = itemAdapterBase.clearHighlights()

    override fun highlightText(highlight: ListItemHighlight) =
        itemAdapterBase.highlightText(highlight)

    internal fun selectHighlight(pos: Int) = itemAdapterBase.selectHighlight(pos)

    internal fun notifyListItemChanged(id: Int) {
        val list = currentList
        val index = list.indexOfFirst { it.id == id }
        val item = list[index]
        if (item.isChild) {
            notifyItemChanged(index)
        } else {
            notifyItemRangeChanged(index, item.children.size + 1)
        }
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<ListItem>() {
                override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem) =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem) =
                    oldItem.body == newItem.body &&
                        oldItem.isChild == newItem.isChild &&
                        oldItem.checked == newItem.checked
            }
    }
}
