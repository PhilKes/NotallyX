package com.omgodse.notally.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.omgodse.notally.databinding.RecyclerPreviewImageBinding
import com.omgodse.notally.model.FileAttachment
import com.omgodse.notally.recyclerview.viewholder.PreviewImageVH
import java.io.File

class PreviewImageAdapter(
    private val imageRoot: File?,
    private val onClick: (position: Int) -> Unit,
) : ListAdapter<FileAttachment, PreviewImageVH>(DiffCallback) {

    override fun onBindViewHolder(holder: PreviewImageVH, position: Int) {
        val image = getItem(position)
        val file = if (imageRoot != null) File(imageRoot, image.localName) else null
        holder.bind(file)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewImageVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerPreviewImageBinding.inflate(inflater, parent, false)
        return PreviewImageVH(binding, onClick)
    }

    private object DiffCallback : DiffUtil.ItemCallback<FileAttachment>() {

        override fun areItemsTheSame(oldItem: FileAttachment, newItem: FileAttachment): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: FileAttachment, newItem: FileAttachment): Boolean {
            return oldItem == newItem
        }
    }
}
