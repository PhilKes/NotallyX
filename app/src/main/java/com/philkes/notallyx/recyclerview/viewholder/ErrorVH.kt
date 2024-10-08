package com.philkes.notallyx.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ErrorBinding
import com.philkes.notallyx.image.FileError

class ErrorVH(private val binding: ErrorBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(error: FileError) {
        binding.Name.text = error.name
        binding.Description.text = error.description
    }
}
