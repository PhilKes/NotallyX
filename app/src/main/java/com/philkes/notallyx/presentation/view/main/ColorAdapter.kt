package com.philkes.notallyx.presentation.view.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.presentation.view.misc.ItemListener

class ColorAdapter(
    private val colors: List<String>,
    private val selectedColor: String?,
    private val listener: ItemListener,
) : RecyclerView.Adapter<ColorVH>() {

    override fun getItemCount() = colors.size

    override fun onBindViewHolder(holder: ColorVH, position: Int) {
        val color = colors[position]
        holder.bind(color, color == selectedColor)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerColorBinding.inflate(inflater, parent, false)
        return ColorVH(binding, listener)
    }
}
