package com.philkes.notallyx.presentation.view.main

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.presentation.view.note.listitem.ItemListener
import com.philkes.notallyx.utils.Operations

class ColorVH(private val binding: RecyclerColorBinding, listener: ItemListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.CardView.setOnClickListener { listener.onClick(adapterPosition) }
    }

    fun bind(color: Color) {
        val value = Operations.extractColor(color, binding.root.context)
        binding.CardView.setCardBackgroundColor(value)
    }
}
