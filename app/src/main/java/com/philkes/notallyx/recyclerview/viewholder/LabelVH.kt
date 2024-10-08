package com.philkes.notallyx.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerLabelBinding
import com.philkes.notallyx.recyclerview.ItemListener

class LabelVH(private val binding: RecyclerLabelBinding, listener: ItemListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.setOnClickListener { listener.onClick(adapterPosition) }

        binding.root.setOnLongClickListener {
            listener.onLongClick(adapterPosition)
            return@setOnLongClickListener true
        }
    }

    fun bind(value: String) {
        binding.root.text = value
    }
}
