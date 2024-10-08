package com.philkes.notallyx.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerBaseNoteBinding
import com.philkes.notallyx.databinding.RecyclerHeaderBinding
import com.philkes.notallyx.model.BaseNote
import com.philkes.notallyx.model.Header
import com.philkes.notallyx.model.Item
import com.philkes.notallyx.recyclerview.ItemListener
import com.philkes.notallyx.recyclerview.viewholder.BaseNoteVH
import com.philkes.notallyx.recyclerview.viewholder.HeaderVH
import java.io.File

class BaseNoteAdapter(
    private val selectedIds: Set<Long>,
    private val dateFormat: String,
    private val textSize: String,
    private val maxItems: Int,
    private val maxLines: Int,
    private val maxTitle: Int,
    private val imageRoot: File?,
    private val listener: ItemListener,
) : ListAdapter<Item, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Header -> 0
            is BaseNote -> 1
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Header -> (holder as HeaderVH).bind(item)
            is BaseNote ->
                (holder as BaseNoteVH).bind(item, imageRoot, selectedIds.contains(item.id))
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

    private fun handleCheck(holder: RecyclerView.ViewHolder, position: Int) {
        val baseNote = getItem(position) as BaseNote
        (holder as BaseNoteVH).updateCheck(selectedIds.contains(baseNote.id))
    }

    private object DiffCallback : DiffUtil.ItemCallback<Item>() {

        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when (oldItem) {
                is BaseNote ->
                    if (newItem is BaseNote) {
                        oldItem.id == newItem.id
                    } else false
                is Header ->
                    if (newItem is Header) {
                        oldItem.label == newItem.label
                    } else false
            }
        }

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when (oldItem) {
                is BaseNote ->
                    if (newItem is BaseNote) {
                        oldItem == newItem
                    } else false
                is Header ->
                    if (newItem is Header) {
                        oldItem.label == newItem.label
                    } else false
            }
        }
    }
}
