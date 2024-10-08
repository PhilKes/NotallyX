package com.philkes.notallyx.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.RecyclerPreviewFileBinding
import com.philkes.notallyx.model.FileAttachment

class PreviewFileVH(
    private val binding: RecyclerPreviewFileBinding,
    onClick: (position: Int) -> Unit,
    onLongClick: (position: Int) -> Boolean,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.FileName.setOnClickListener { onClick(adapterPosition) }
        binding.FileName.setOnLongClickListener { onLongClick(adapterPosition) }
    }

    fun bind(fileAttachment: FileAttachment) {
        binding.FileName.text = fileAttachment.originalName
        binding.FileName.setChipIconResource(getIconForMimeType(fileAttachment.mimeType))
    }

    private fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.add_images
            mimeType.startsWith("video/") -> R.drawable.video
            mimeType.startsWith("audio/") -> R.drawable.record_audio
            mimeType.startsWith("application/zip") -> R.drawable.archive
            else -> R.drawable.text_file
        }
    }
}
