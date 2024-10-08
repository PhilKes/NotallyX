package com.philkes.notallyx.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.ErrorBinding
import com.philkes.notallyx.image.FileError
import com.philkes.notallyx.recyclerview.viewholder.ErrorVH

class ErrorAdapter(private val items: List<FileError>) : RecyclerView.Adapter<ErrorVH>() {

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ErrorVH, position: Int) {
        val error = items[position]
        holder.bind(error)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ErrorVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ErrorBinding.inflate(inflater, parent, false)
        return ErrorVH(binding)
    }
}
