package com.philkes.notallyx.presentation.view.main

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.RecyclerBaseNoteBinding
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.presentation.view.note.listitem.ListItemListener
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.applySpans
import com.philkes.notallyx.utils.displayFormattedTimestamp
import com.philkes.notallyx.utils.dp
import com.philkes.notallyx.utils.getQuantityString
import java.io.File

class BaseNoteVH(
    private val binding: RecyclerBaseNoteBinding,
    private val dateFormat: String,
    private val textSize: String,
    private val maxItems: Int,
    maxLines: Int,
    maxTitle: Int,
    listener: ListItemListener,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        val title = TextSize.getDisplayTitleSize(textSize)
        val body = TextSize.getDisplayBodySize(textSize)

        binding.apply {
            Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
            Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            Note.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            LinearLayout.children.forEach { view ->
                view as TextView
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            }

            Title.maxLines = maxTitle
            Note.maxLines = maxLines

            root.setOnClickListener { listener.onClick(adapterPosition) }

            root.setOnLongClickListener {
                listener.onLongClick(adapterPosition)
                return@setOnLongClickListener true
            }
        }
    }

    fun updateCheck(checked: Boolean) {
        binding.root.isChecked = checked
    }

    fun bind(baseNote: BaseNote, imageRoot: File?, checked: Boolean) {
        updateCheck(checked)

        when (baseNote.type) {
            Type.NOTE -> bindNote(baseNote.body, baseNote.spans)
            Type.LIST -> bindList(baseNote.items)
        }

        binding.Date.displayFormattedTimestamp(baseNote.timestamp, dateFormat)
        setColor(baseNote.color)
        setImages(baseNote.images, imageRoot)
        setFiles(baseNote.files)

        binding.Title.apply {
            text = baseNote.title
            isVisible = baseNote.title.isNotEmpty()
        }

        Operations.bindLabels(binding.LabelGroup, baseNote.labels, textSize)

        if (isEmpty(baseNote)) {
            binding.Title.apply {
                setText(getEmptyMessage(baseNote))
                visibility = View.VISIBLE
            }
        }
    }

    private fun bindNote(body: String, spans: List<SpanRepresentation>) {
        binding.LinearLayout.visibility = View.GONE

        binding.Note.apply {
            text = body.applySpans(spans)
            isVisible = body.isNotEmpty()
        }
    }

    private fun bindList(items: List<ListItem>) {
        binding.apply {
            Note.visibility = View.GONE
            if (items.isEmpty()) {
                LinearLayout.visibility = View.GONE
            } else {
                LinearLayout.visibility = View.VISIBLE
                val filteredList = items.take(maxItems)
                LinearLayout.children.forEachIndexed { index, view ->
                    if (view.id != R.id.ItemsRemaining) {
                        if (index < filteredList.size) {
                            val item = filteredList[index]
                            (view as TextView).apply {
                                text = item.body
                                handleChecked(this, item.checked)
                                visibility = View.VISIBLE
                                if (item.isChild) {
                                    val layoutParams = layoutParams as LinearLayout.LayoutParams
                                    layoutParams.marginStart = 150.dp
                                    setLayoutParams(layoutParams)
                                }
                            }
                        } else view.visibility = View.GONE
                    }
                }

                if (items.size > maxItems) {
                    ItemsRemaining.apply {
                        visibility = View.VISIBLE
                        text = (items.size - maxItems).toString()
                    }
                } else ItemsRemaining.visibility = View.GONE
            }
        }
    }

    private fun setColor(color: Color) {
        binding.root.apply {
            if (color == Color.DEFAULT) {
                val stroke = ContextCompat.getColorStateList(context, R.color.chip_stroke)
                setStrokeColor(stroke)
                setCardBackgroundColor(0)
            } else {
                strokeColor = 0
                val colorInt = Operations.extractColor(color, context)
                setCardBackgroundColor(colorInt)
            }
        }
    }

    private fun setImages(images: List<FileAttachment>, mediaRoot: File?) {

        binding.apply {
            if (images.isNotEmpty()) {
                ImageView.visibility = View.VISIBLE
                Message.visibility = View.GONE

                val image = images[0]
                val file = if (mediaRoot != null) File(mediaRoot, image.localName) else null

                Glide.with(ImageView)
                    .load(file)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .listener(
                        object : RequestListener<Drawable> {

                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean,
                            ): Boolean {
                                Message.visibility = View.VISIBLE
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: DataSource?,
                                isFirstResource: Boolean,
                            ): Boolean {
                                return false
                            }
                        }
                    )
                    .into(ImageView)
                if (images.size > 1) {
                    ImageViewMore.apply {
                        text = images.size.toString()
                        visibility = View.VISIBLE
                    }
                } else {
                    ImageViewMore.visibility = View.GONE
                }
            } else {
                ImageView.visibility = View.GONE
                Message.visibility = View.GONE
                ImageViewMore.visibility = View.GONE
                Glide.with(ImageView).clear(ImageView)
            }
        }
    }

    private fun setFiles(files: List<FileAttachment>) {
        binding.apply {
            if (files.isNotEmpty()) {
                FileView.visibility = View.VISIBLE
                FileView.text = files[0].originalName
                if (files.size > 1) {
                    FileViewMore.apply {
                        text = getQuantityString(R.plurals.more_files, files.size - 1)
                        visibility = View.VISIBLE
                    }
                } else {
                    FileViewMore.visibility = View.GONE
                }
            } else {
                FileView.visibility = View.GONE
                FileViewMore.visibility = View.GONE
            }
        }
    }

    private fun isEmpty(baseNote: BaseNote): Boolean {
        return with(baseNote) {
            when (type) {
                Type.NOTE -> title.isBlank() && body.isBlank() && images.isEmpty()
                Type.LIST -> title.isBlank() && items.isEmpty() && images.isEmpty()
            }
        }
    }

    private fun getEmptyMessage(baseNote: BaseNote): Int {
        return when (baseNote.type) {
            Type.NOTE -> R.string.empty_note
            Type.LIST -> R.string.empty_list
        }
    }

    private fun handleChecked(textView: TextView, checked: Boolean) {
        if (checked) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.checkbox_16,
                0,
                0,
                0,
            )
        } else
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.checkbox_outline_16,
                0,
                0,
                0,
            )
    }
}
