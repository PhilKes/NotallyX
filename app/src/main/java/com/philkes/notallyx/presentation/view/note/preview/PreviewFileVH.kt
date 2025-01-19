package com.philkes.notallyx.presentation.view.note.preview

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.RecyclerPreviewFileBinding
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews

class PreviewFileVH(
    private val binding: RecyclerPreviewFileBinding,
    onClick: (position: Int) -> Unit,
    onLongClick: (position: Int) -> Boolean,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.FileName.apply {
            setOnClickListener { onClick(adapterPosition) }
            setOnLongClickListener { onLongClick(adapterPosition) }
        }
    }

    fun bind(fileAttachment: FileAttachment, color: Int?) {
        binding.FileName.apply {
            text = fileAttachment.originalName
            setChipIconResource(getIconForMimeType(fileAttachment.mimeType))
            color?.let { setControlsContrastColorForAllViews(it) }
        }
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
