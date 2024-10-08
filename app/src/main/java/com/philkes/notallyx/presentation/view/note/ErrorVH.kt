package com.philkes.notallyx.presentation.view.note

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ErrorBinding
import com.philkes.notallyx.utils.FileError

class ErrorVH(private val binding: ErrorBinding) : RecyclerView.ViewHolder(binding.root) {

    fun bind(error: FileError) {
        binding.Name.text = error.name
        binding.Description.text = error.description
    }
}
