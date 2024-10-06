package com.omgodse.notally.recyclerview.viewholder

import androidx.recyclerview.widget.RecyclerView
import com.omgodse.notally.R
import com.omgodse.notally.databinding.RecyclerPreviewFileBinding
import com.omgodse.notally.model.FileAttachment

class PreviewFileVH(
    private val binding: RecyclerPreviewFileBinding,
    onClick: (position: Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.FileName.setOnClickListener { onClick(adapterPosition) }
        // TODO: On long click allow to delete file attachment
    }

    fun bind(fileAttachment: FileAttachment) {
        binding.FileName.text = fileAttachment.originalName
        binding.FileName.setCompoundDrawablesRelativeWithIntrinsicBounds(
            getIconForMimeType(fileAttachment.mimeType),
            0,
            0,
            0,
        )
    }

    private fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.add_images
            mimeType.startsWith("video/") -> R.drawable.video
            mimeType.startsWith("audio/") -> R.drawable.record_audio
            mimeType.startsWith("application/pdf") -> R.drawable.pdf
            mimeType.startsWith("application/zip") -> R.drawable.archive
            else -> R.drawable.text_file
        }
    }
}
