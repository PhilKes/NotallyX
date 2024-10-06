package com.omgodse.notally.recyclerview.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.omgodse.notally.databinding.RecyclerPreviewFileBinding
import com.omgodse.notally.model.FileAttachment
import com.omgodse.notally.recyclerview.viewholder.PreviewFileVH

class PreviewFileAdapter(private val onClick: (fileAttachment: FileAttachment) -> Unit) :
    ListAdapter<FileAttachment, PreviewFileVH>(DiffCallback) {

    override fun onBindViewHolder(holder: PreviewFileVH, position: Int) {
        val fileAttachment = getItem(position)
        holder.bind(fileAttachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewFileVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerPreviewFileBinding.inflate(inflater, parent, false)
        return PreviewFileVH(binding) { position -> onClick.invoke(getItem(position)) }
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
