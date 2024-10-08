package com.philkes.notallyx.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.miscellaneous.Operations
import com.philkes.notallyx.model.Color
import com.philkes.notallyx.recyclerview.ItemListener

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
