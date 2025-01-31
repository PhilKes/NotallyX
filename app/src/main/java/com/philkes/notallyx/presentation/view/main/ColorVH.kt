package com.philkes.notallyx.presentation.view.main

import android.content.res.ColorStateList
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.databinding.RecyclerColorBinding
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getColorFromAttr
import com.philkes.notallyx.presentation.getContrastFontColor
import com.philkes.notallyx.presentation.view.misc.ItemListener

class ColorVH(private val binding: RecyclerColorBinding, listener: ItemListener) :
    RecyclerView.ViewHolder(binding.root) {

    init {
        binding.CardView.setOnClickListener { listener.onClick(absoluteAdapterPosition) }
        binding.CardView.setOnLongClickListener {
            listener.onLongClick(absoluteAdapterPosition)
            true
        }
    }

    fun bind(color: String, isSelected: Boolean) {
        val showAddIcon = color == BaseNote.COLOR_NEW
        val context = binding.root.context
        val value =
            if (showAddIcon) context.getColorFromAttr(R.attr.colorOnSurface)
            else context.extractColor(color)
        val controlsColor = context.getContrastFontColor(value)
        binding.apply {
            CardView.apply {
                setCardBackgroundColor(value)
                contentDescription = color
                if (isSelected) {
                    strokeWidth = 4.dp
                    strokeColor = controlsColor
                } else {
                    strokeWidth = 1.dp
                    strokeColor = controlsColor
                }
            }
            CardIcon.apply {
                if (showAddIcon) {
                    setImageResource(R.drawable.add)
                } else if (isSelected) {
                    setImageResource(R.drawable.checked_circle)
                }
                imageTintList = ColorStateList.valueOf(controlsColor)
                isVisible = showAddIcon || isSelected
            }
        }
    }
}
