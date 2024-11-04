package com.philkes.notallyx.presentation.view.main.label

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.RecyclerLabelBinding

class LabelVH(private val binding: RecyclerLabelBinding, listener: LabelListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.apply {
            LabelText.setOnClickListener { listener.onClick(adapterPosition) }
            EditButton.setOnClickListener { listener.onEdit(adapterPosition) }
            DeleteButton.setOnClickListener { listener.onDelete(adapterPosition) }
            VisibilityButton.setOnClickListener { listener.onToggleVisibility(adapterPosition) }
        }
    }

    fun bind(value: LabelData) {
        binding.LabelText.text = value.label
        binding.VisibilityButton.setImageResource(
            if (value.visibleInNavigation) R.drawable.visibility else R.drawable.visibility_off
        )
    }
}
