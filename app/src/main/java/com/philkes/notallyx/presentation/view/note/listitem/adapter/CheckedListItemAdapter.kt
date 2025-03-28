package com.philkes.notallyx.presentation.view.note.listitem.adapter

import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.HighlightText
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NoteViewMode
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class CheckedListItemAdapter(
    @ColorInt var backgroundColor: Int,
    private val textSize: TextSize,
    elevation: Float,
    private val preferences: NotallyXPreferences,
    private val listManager: ListManager,
    private val isCheckedListAdapter: Boolean,
    scrollView: NestedScrollView,
) : RecyclerView.Adapter<ListItemVH>(), HighlightText {

    private lateinit var list: SortedList<ListItem>

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
            override fun getItem(position: Int): ListItem = list[position]
        }

    var viewMode: NoteViewMode = NoteViewMode.EDIT
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    internal fun setList(list: SortedList<ListItem>) {
        this.list = list
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        itemAdapterBase.onAttachedToRecyclerView(recyclerView)
    }

    override fun onBindViewHolder(holder: ListItemVH, position: Int) {
        itemAdapterBase.onBindViewHolder(holder, position, viewMode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        itemAdapterBase.onCreateViewHolder(parent, viewType)

    internal fun setBackgroundColor(@ColorInt color: Int) =
        itemAdapterBase.setBackgroundColor(color)

    internal fun clearHighlights() = itemAdapterBase.clearHighlights()

    override fun highlightText(highlight: ListItemHighlight) =
        itemAdapterBase.highlightText(highlight)

    internal fun selectHighlight(pos: Int) = itemAdapterBase.selectHighlight(pos)
}
