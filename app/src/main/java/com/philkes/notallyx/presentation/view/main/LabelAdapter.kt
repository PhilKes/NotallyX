package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.databinding.RecyclerLabelBinding
import com.philkes.notallyx.presentation.view.misc.StringDiffCallback
import com.philkes.notallyx.presentation.view.note.listitem.ItemListener

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
