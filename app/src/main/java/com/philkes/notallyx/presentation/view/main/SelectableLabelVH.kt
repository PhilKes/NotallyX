package com.philkes.notallyx.presentation.view.main

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerSelectableLabelBinding

class SelectableLabelVH(
    private val binding: RecyclerSelectableLabelBinding,
    private val onChecked: (position: Int, checked: Boolean) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.setOnCheckedChangeListener { _, isChecked ->
            onChecked(absoluteAdapterPosition, isChecked)
        }
    }

    fun bind(value: String, checked: Boolean) {
        binding.root.apply {
            text = value
            isChecked = checked
        }
    }
}
