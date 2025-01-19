package com.philkes.notallyx.presentation.view.note.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.RecyclerPreviewFileBinding

class PreviewFileAdapter(
    private val onClick: (fileAttachment: FileAttachment) -> Unit,
    private val onLongClick: (fileAttachment: FileAttachment) -> Boolean,
) : ListAdapter<FileAttachment, PreviewFileVH>(DiffCallback) {

    @ColorInt private var color: Int? = null

    fun setColor(@ColorInt colorInt: Int) {
        color = colorInt
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: PreviewFileVH, position: Int) {
        val fileAttachment = getItem(position)
        holder.bind(fileAttachment, color)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewFileVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerPreviewFileBinding.inflate(inflater, parent, false)
        return PreviewFileVH(binding, { position -> onClick.invoke(getItem(position)) }) { position
            ->
            onLongClick.invoke(getItem(position))
        }
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
