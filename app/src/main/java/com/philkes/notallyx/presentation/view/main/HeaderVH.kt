package com.philkes.notallyx.presentation.view.main

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.databinding.RecyclerHeaderBinding

class HeaderVH(private val binding: RecyclerHeaderBinding) : RecyclerView.ViewHolder(binding.root) {

    init {
        val params = binding.root.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) {
            params.isFullSpan = true
        }
    }

    fun bind(header: Header) {
        binding.root.text = header.label
    }
}
