package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import androidx.recyclerview.widget.SortedListAdapterCallback
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.databinding.RecyclerBaseNoteBinding
import com.philkes.notallyx.databinding.RecyclerHeaderBinding
import com.philkes.notallyx.presentation.view.main.sorting.BaseNoteCreationDateSort
import com.philkes.notallyx.presentation.view.main.sorting.BaseNoteModifiedDateSort
import com.philkes.notallyx.presentation.view.main.sorting.BaseNoteTitleSort
import com.philkes.notallyx.presentation.view.misc.NotesSorting.autoSortByModifiedDate
import com.philkes.notallyx.presentation.view.misc.NotesSorting.autoSortByTitle
import com.philkes.notallyx.presentation.view.misc.SortDirection
import com.philkes.notallyx.presentation.view.note.listitem.ListItemListener
import java.io.File

class BaseNoteAdapter(
    private val selectedIds: Set<Long>,
    private val dateFormat: String,
    private val sortedBy: String,
    private val textSize: String,
    private val maxItems: Int,
    private val maxLines: Int,
    private val maxTitle: Int,
    private val imageRoot: File?,
    private val listener: ListItemListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var list =
        SortedList(Item::class.java, BaseNoteCreationDateSort(this, SortDirection.ASC))

    override fun getItemViewType(position: Int): Int {
        return when (list[position]) {
            is Header -> 0
            is BaseNote -> 1
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = list[position]) {
            is Header -> (holder as HeaderVH).bind(item)
            is BaseNote ->
                (holder as BaseNoteVH).bind(
                    item,
                    imageRoot,
                    selectedIds.contains(item.id),
                    sortedBy,
                )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else handleCheck(holder, position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> {
                val binding = RecyclerHeaderBinding.inflate(inflater, parent, false)
                HeaderVH(binding)
            }
            else -> {
                val binding = RecyclerBaseNoteBinding.inflate(inflater, parent, false)
                BaseNoteVH(binding, dateFormat, textSize, maxItems, maxLines, maxTitle, listener)
            }
        }
    }

    fun setSorting(sortBy: String, sortDirection: SortDirection) {
        val sortCallback =
            when (sortBy) {
                autoSortByTitle -> BaseNoteTitleSort(this, sortDirection)
                autoSortByModifiedDate -> BaseNoteModifiedDateSort(this, sortDirection)
                else -> BaseNoteCreationDateSort(this, sortDirection)
            }
        replaceSorting(sortCallback)
    }

    fun getItem(position: Int): Item? {
        return list[position]
    }

    val currentList: List<Item>
        get() = list.toList()

    fun submitList(items: List<Item>) {
        list.replaceAll(items)
    }

    private fun replaceSorting(sortCallback: SortedListAdapterCallback<Item>) {
        val mutableList = mutableListOf<Item>()
        for (i in 0 until list.size()) {
            mutableList.add(list[i])
        }
        list.clear()
        list = SortedList(Item::class.java, sortCallback)
        list.addAll(mutableList)
    }

    private fun handleCheck(holder: RecyclerView.ViewHolder, position: Int) {
        val baseNote = list[position] as BaseNote
        (holder as BaseNoteVH).updateCheck(selectedIds.contains(baseNote.id))
    }

    private fun <T> SortedList<T>.toList(): List<T> {
        val mutableList = mutableListOf<T>()
        for (i in 0 until this.size()) {
            mutableList.add(this[i])
        }
        return mutableList.toList()
    }
}
