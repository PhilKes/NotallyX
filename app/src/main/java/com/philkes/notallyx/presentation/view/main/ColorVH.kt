package com.philkes.notallyx.presentation.view.main

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.view.misc.ItemListener

class ColorVH(private val binding: RecyclerColorBinding, listener: ItemListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.CardView.setOnClickListener { listener.onClick(adapterPosition) }
    }

    fun bind(color: Color) {
        val value = binding.root.context.extractColor(color)
        binding.CardView.setCardBackgroundColor(value)
        binding.CardView.contentDescription = color.name
    }
}
