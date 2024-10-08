package com.philkes.notallyx.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.databinding.RecyclerImageBinding
import com.philkes.notallyx.model.FileAttachment
import com.philkes.notallyx.recyclerview.viewholder.ImageVH
import java.io.File

class ImageAdapter(private val mediaRoot: File?, val items: ArrayList<FileAttachment>) :
    RecyclerView.Adapter<ImageVH>() {

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ImageVH, position: Int) {
        val image = items[position]
        val file = if (mediaRoot != null) File(mediaRoot, image.localName) else null
        holder.bind(file)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerImageBinding.inflate(inflater, parent, false)
        return ImageVH(binding)
    }
}
