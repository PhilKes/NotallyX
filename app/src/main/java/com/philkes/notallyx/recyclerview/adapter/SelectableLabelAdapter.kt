package com.philkes.notallyx.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.databinding.RecyclerSelectableLabelBinding
import com.philkes.notallyx.recyclerview.StringDiffCallback
import com.philkes.notallyx.recyclerview.viewholder.SelectableLabelVH

class SelectableLabelAdapter(private val selectedLabels: List<String>) :
    ListAdapter<String, SelectableLabelVH>(StringDiffCallback()) {

    var onChecked: ((position: Int, checked: Boolean) -> Unit)? = null

    override fun onBindViewHolder(holder: SelectableLabelVH, position: Int) {
        val label = getItem(position)
        holder.bind(label, selectedLabels.contains(label))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableLabelVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerSelectableLabelBinding.inflate(inflater, parent, false)
        return SelectableLabelVH(binding, requireNotNull(onChecked))
    }
}
