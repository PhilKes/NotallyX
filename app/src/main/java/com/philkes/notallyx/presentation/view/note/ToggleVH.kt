package com.philkes.notallyx.presentation.view.note

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerToggleBinding

class ToggleVH(private val binding: RecyclerToggleBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(toggle: Toggle) {
        binding.root.apply {
            setIconResource(toggle.drawable)
            contentDescription = context.getString(toggle.titleResId)
            setOnClickListener { toggle.onToggle(toggle) }
            isChecked = toggle.checked
        }
    }
}
