package com.philkes.notallyx.presentation.view.main.label

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.databinding.RecyclerLabelBinding

class LabelAdapter(private val listener: LabelListener) :
    ListAdapter<LabelData, LabelVH>(DiffCallback) {

    override fun onBindViewHolder(holder: LabelVH, position: Int) {
        val label = getItem(position)
        holder.bind(label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerLabelBinding.inflate(inflater, parent, false)
        return LabelVH(binding, listener)
    }
}

data class LabelData(val label: String, var visibleInNavigation: Boolean)

private object DiffCallback : DiffUtil.ItemCallback<LabelData>() {

    override fun areItemsTheSame(oldItem: LabelData, newItem: LabelData): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: LabelData, newItem: LabelData): Boolean {
        return oldItem == newItem
    }
}
