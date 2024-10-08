package com.philkes.notallyx.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.databinding.RecyclerLabelBinding
import com.philkes.notallyx.recyclerview.ItemListener
import com.philkes.notallyx.recyclerview.StringDiffCallback
import com.philkes.notallyx.recyclerview.viewholder.LabelVH

class LabelAdapter(private val listener: ItemListener) :
    ListAdapter<String, LabelVH>(StringDiffCallback()) {

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
