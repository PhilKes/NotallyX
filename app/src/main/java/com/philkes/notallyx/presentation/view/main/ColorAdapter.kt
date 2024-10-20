package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.presentation.view.note.listitem.ListItemListener

class ColorAdapter(private val listener: ListItemListener) : RecyclerView.Adapter<ColorVH>() {

    private val colors = Color.values()

    override fun getItemCount() = colors.size

    override fun onBindViewHolder(holder: ColorVH, position: Int) {
        val color = colors[position]
        holder.bind(color)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerColorBinding.inflate(inflater, parent, false)
        return ColorVH(binding, listener)
    }
}
