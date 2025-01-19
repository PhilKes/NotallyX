package com.philkes.notallyx.presentation.view.main

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getColorFromAttr
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import com.philkes.notallyx.utils.Operations
import java.io.File

data class BaseNoteVHPreferences(
    val textSize: TextSize,
    val maxItems: Int,
    val maxLines: Int,
    val maxTitleLines: Int,
    val hideLabels: Boolean,
)

class BaseNoteVH(
    private val binding: RecyclerBaseNoteBinding,
    private val dateFormat: DateFormat,
    private val preferences: BaseNoteVHPreferences,
    listener: ItemListener,
) : RecyclerView.ViewHolder(binding.root) {

    init {
        val title = preferences.textSize.displayTitleSize
        val body = preferences.textSize.displayBodySize

        binding.apply {
            Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
            Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            Note.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            LinearLayout.children.forEach { view ->
                view as TextView
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
            }

            Title.maxLines = preferences.maxTitleLines
            Note.maxLines = preferences.maxLines

            root.setOnClickListener { listener.onClick(adapterPosition) }

            root.setOnLongClickListener {
                listener.onLongClick(adapterPosition)
                return@setOnLongClickListener true
            }
        }
    }

    fun updateCheck(checked: Boolean, color: Color) {
        if (binding.root.isChecked != checked) {
            if (checked) {
                binding.root.apply {
                    strokeColor = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
                    strokeWidth = 3.dp(context)
                }
            } else {
                binding.root.apply {
                    strokeColor =
                        if (color == Color.DEFAULT)
                            ContextCompat.getColor(context, R.color.chip_stroke)
                        else 0
                    strokeWidth = 1.dp(context)
                }
            }
        }
        binding.root.isChecked = checked
    }

    fun bind(baseNote: BaseNote, imageRoot: File?, checked: Boolean, sortBy: NotesSortBy) {
        updateCheck(checked, baseNote.color)

        when (baseNote.type) {
            Type.NOTE -> bindNote(baseNote.body, baseNote.spans, baseNote.title.isEmpty())
            Type.LIST -> bindList(baseNote.items)
        }
        val (date, datePrefixResId) =
            when (sortBy) {
                NotesSortBy.CREATION_DATE -> Pair(baseNote.timestamp, R.string.creation_date)
                NotesSortBy.MODIFIED_DATE ->
                    Pair(baseNote.modifiedTimestamp, R.string.modified_date)
                else -> Pair(null, null)
            }
        binding.Date.displayFormattedTimestamp(date, dateFormat, datePrefixResId)

        setImages(baseNote.images, imageRoot)
        setFiles(baseNote.files)

        binding.Title.apply {
            text = baseNote.title
            isVisible = baseNote.title.isNotEmpty()
            updatePadding(
                bottom =
                    if (baseNote.hasNoContents() || shouldOnlyDisplayTitle(baseNote)) 0
                    else 8.dp(context)
            )
            setCompoundDrawablesWithIntrinsicBounds(
                if (baseNote.type == Type.LIST && preferences.maxItems < 1)
                    R.drawable.checkbox_small
                else 0,
                0,
                0,
                0,
            )
        }

        if (preferences.hideLabels) {
            binding.LabelGroup.visibility = GONE
        } else {
            Operations.bindLabels(
                binding.LabelGroup,
                baseNote.labels,
                preferences.textSize,
                binding.Note.isVisible || binding.Title.isVisible,
            )
        }

        if (baseNote.isEmpty()) {
            binding.Title.apply {
                setText(baseNote.getEmptyMessage())
                isVisible = true
            }
        }
        setColor(baseNote.color)
    }

    private fun bindNote(body: String, spans: List<SpanRepresentation>, isTitleEmpty: Boolean) {
        binding.LinearLayout.visibility = GONE

        binding.Note.apply {
            text = body.applySpans(spans)
            if (preferences.maxLines < 1) {
                isVisible = isTitleEmpty
                maxLines = if (isTitleEmpty) 1 else preferences.maxLines
            } else {
                isVisible = body.isNotEmpty()
            }
        }
    }

    private fun bindList(items: List<ListItem>) {
        binding.apply {
            Note.visibility = GONE
            if (items.isEmpty()) {
                LinearLayout.visibility = GONE
            } else {
                LinearLayout.visibility = VISIBLE
                val filteredList = items.take(preferences.maxItems)
                LinearLayout.children.forEachIndexed { index, view ->
                    if (view.id != R.id.ItemsRemaining) {
                        if (index < filteredList.size) {
                            val item = filteredList[index]
                            (view as TextView).apply {
                                text = item.body
                                handleChecked(this, item.checked)
                                visibility = VISIBLE
                                if (item.isChild) {
                                    updateLayoutParams<LinearLayout.LayoutParams> {
                                        marginStart = 20.dp(context)
                                    }
                                }
                            }
                        } else view.visibility = GONE
                    }
                }

                if (preferences.maxItems > 0 && items.size > preferences.maxItems) {
                    ItemsRemaining.apply {
                        visibility = VISIBLE
                        text = (items.size - preferences.maxItems).toString()
                    }
                } else ItemsRemaining.visibility = GONE
            }
        }
    }

    private fun setColor(color: Color) {
        binding.root.apply {
            if (color == Color.DEFAULT) {
                val stroke = ContextCompat.getColorStateList(context, R.color.chip_stroke)
                setStrokeColor(stroke)
                setCardBackgroundColor(0)
                setControlsContrastColorForAllViews(context.getColorFromAttr(R.attr.colorSurface))
            } else {
                strokeColor = 0
                val colorInt = context.extractColor(color)
                setCardBackgroundColor(colorInt)
                setControlsContrastColorForAllViews(colorInt)
            }
        }
    }

    private fun setImages(images: List<FileAttachment>, mediaRoot: File?) {

        binding.apply {
            if (images.isNotEmpty()) {
                ImageView.visibility = VISIBLE
                Message.visibility = GONE

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
                                Message.visibility = VISIBLE
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
                        visibility = VISIBLE
                    }
                } else {
                    ImageViewMore.visibility = GONE
                }
            } else {
                ImageView.visibility = GONE
                Message.visibility = GONE
                ImageViewMore.visibility = GONE
                Glide.with(ImageView).clear(ImageView)
            }
        }
    }

    private fun setFiles(files: List<FileAttachment>) {
        binding.apply {
            if (files.isNotEmpty()) {
                FileViewLayout.visibility = VISIBLE
                FileView.text = files[0].originalName
                if (files.size > 1) {
                    FileViewMore.apply {
                        text = getQuantityString(R.plurals.more_files, files.size - 1)
                        visibility = VISIBLE
                    }
                } else {
                    FileViewMore.visibility = GONE
                }
            } else {
                FileViewLayout.visibility = GONE
            }
        }
    }

    private fun shouldOnlyDisplayTitle(baseNote: BaseNote) =
        when (baseNote.type) {
            Type.NOTE -> preferences.maxLines < 1
            Type.LIST -> preferences.maxItems < 1
        }

    private fun BaseNote.isEmpty() = title.isBlank() && hasNoContents() && images.isEmpty()

    private fun BaseNote.hasNoContents() = body.isEmpty() && items.isEmpty()

    private fun BaseNote.getEmptyMessage() =
        when (type) {
            Type.NOTE -> R.string.empty_note
            Type.LIST -> R.string.empty_list
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
