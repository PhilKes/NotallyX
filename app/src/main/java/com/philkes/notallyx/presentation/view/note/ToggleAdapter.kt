package com.philkes.notallyx.presentation.view.note

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerToggleBinding

open class ToggleAdapter(protected val toggles: MutableList<Toggle>) :
    RecyclerView.Adapter<ToggleVH>() {

    override fun getItemCount() = toggles.size

    override fun onBindViewHolder(holder: ToggleVH, position: Int) {
        val toggle = toggles[position]
        holder.bind(toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToggleVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerToggleBinding.inflate(inflater, parent, false)
        return ToggleVH(binding)
    }
}

data class Toggle(
    @StringRes val titleResId: Int,
    @DrawableRes val drawable: Int,
    var checked: Boolean,
    val onToggle: (toggle: Toggle) -> Unit,
)
