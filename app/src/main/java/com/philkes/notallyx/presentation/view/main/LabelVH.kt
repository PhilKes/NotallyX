package com.philkes.notallyx.presentation.view.main

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerLabelBinding
import com.philkes.notallyx.presentation.view.note.listitem.ListItemListener

class LabelVH(private val binding: RecyclerLabelBinding, listener: ListItemListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.apply {
            setOnClickListener { listener.onClick(adapterPosition) }
            setOnLongClickListener {
                listener.onLongClick(adapterPosition)
                return@setOnLongClickListener true
            }
        }
    }

    fun bind(value: String) {
        binding.root.text = value
    }
}
