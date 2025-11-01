package com.philkes.notallyx.presentation.view.main

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import android.widget.TextView
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
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.hasUpcomingNotification
import com.philkes.notallyx.databinding.RecyclerBaseNoteBinding
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.bindLabels
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.view.misc.highlightableview.HighlightableTextView
import com.philkes.notallyx.presentation.view.misc.highlightableview.SEARCH_SNIPPET_ITEM_LINES
import com.philkes.notallyx.presentation.view.note.listitem.init
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import java.io.File

data class BaseNoteVHPreferences(
    val textSize: TextSize,
    val maxItems: Int,
    val maxLines: Int,
    val maxTitleLines: Int,
    val hideLabels: Boolean,
    val hideImages: Boolean,
)

class BaseNoteVH(
    private val binding: RecyclerBaseNoteBinding,
    private val dateFormat: DateFormat,
    private val preferences: BaseNoteVHPreferences,
    listener: ItemListener,
) : RecyclerView.ViewHolder(binding.root) {

    private var searchKeyword: String = ""

    fun setSearchKeyword(keyword: String) {
        this.searchKeyword = keyword
    }

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

            root.setOnClickListener { listener.onClick(absoluteAdapterPosition) }

            root.setOnLongClickListener {
                listener.onLongClick(absoluteAdapterPosition)
                return@setOnLongClickListener true
            }
        }
    }

    fun updateCheck(checked: Boolean, color: String) {
        if (checked) {
            binding.root.strokeWidth = 3.dp
        } else {
            binding.root.strokeWidth = if (color == BaseNote.COLOR_DEFAULT) 1.dp else 0
        }
        binding.root.isChecked = checked
    }

    fun bind(baseNote: BaseNote, imageRoot: File?, checked: Boolean, sortBy: NotesSortBy) {
        updateCheck(checked, baseNote.color)

        when (baseNote.type) {
            Type.NOTE -> bindNote(baseNote, searchKeyword)
            Type.LIST -> bindList(baseNote, searchKeyword)
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
            isVisible = baseNote.title.isNotEmpty()
            updatePadding(
                bottom =
                    if (baseNote.hasNoContents() || shouldOnlyDisplayTitle(baseNote)) 0 else 8.dp
            )
            if (searchKeyword.isNotBlank()) {
                val snippet = extractSearchSnippet(baseNote.title, searchKeyword)
                if (snippet != null) {
                    showSearchSnippet(snippet)
                } else text = baseNote.title
            } else text = baseNote.title

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
            binding.LabelGroup.bindLabels(
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

        binding.RemindersView.isVisible = baseNote.reminders.any { it.hasUpcomingNotification() }
    }

    private fun bindNote(baseNote: BaseNote, keyword: String) {
        binding.LinearLayout.visibility = GONE
        if (keyword.isBlank()) {
            bindNote(baseNote.body.value, baseNote.spans, baseNote.title.isEmpty())
            return
        }
        binding.Note.apply {
            val snippet = extractSearchSnippet(baseNote.body.value, keyword)
            if (snippet == null) {
                bindNote(baseNote.body.value, baseNote.spans, baseNote.title.isEmpty())
            } else {
                showSearchSnippet(snippet)
            }
        }
    }

    private fun bindNote(body: String, spans: List<SpanRepresentation>, isTitleEmpty: Boolean) {
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

    /** Shows a snippet of ListItems around the ListItem that contains keyword */
    private fun LinearLayout.bindListSearch(
        initializedItems: List<ListItem>,
        keyword: String,
        isTitleEmpty: Boolean,
    ) {
        binding.LinearLayout.visibility = VISIBLE
        val keywordItemIdx =
            initializedItems.indexOfFirst { it.body.contains(keyword, ignoreCase = true) }
        if (keywordItemIdx == -1) {
            return bindList(initializedItems, isTitleEmpty)
        }
        val listItemViews = children.filterIsInstance(HighlightableTextView::class.java).toList()
        listItemViews.forEach { it.visibility = GONE }
        val startItemIdx = (keywordItemIdx - SEARCH_SNIPPET_ITEM_LINES).coerceAtLeast(0)
        val endItemIdx =
            (keywordItemIdx + SEARCH_SNIPPET_ITEM_LINES).coerceAtMost(initializedItems.lastIndex)
        (startItemIdx..endItemIdx).forEachIndexed { viewIdx, itemIdx ->
            listItemViews[viewIdx].apply {
                val item = initializedItems[itemIdx]
                text = item.body
                if (itemIdx == keywordItemIdx) {
                    highlight(keyword)
                }
                handleChecked(this, item.checked)
                visibility = VISIBLE
                updateLayoutParams<LinearLayout.LayoutParams> {
                    marginStart = if (item.isChild) 20.dp else 0
                }
            }
        }
        bindItemsRemaining(initializedItems.size, endItemIdx - startItemIdx + 1)
    }

    private fun bindList(baseNote: BaseNote, keyword: String) {
        binding.Note.visibility = GONE
        val initializedItems = baseNote.items.init()
        if (baseNote.items.isEmpty()) {
            binding.LinearLayout.visibility = GONE
            return
        }
        if (keyword.isBlank()) {
            bindList(initializedItems, baseNote.title.isEmpty())
            return
        }
        binding.LinearLayout.bindListSearch(initializedItems, keyword, baseNote.title.isEmpty())
    }

    private fun bindItemsRemaining(totalItems: Int, displayedItems: Int) {
        if (displayedItems > 0 && totalItems > displayedItems) {
            binding.ItemsRemaining.apply {
                visibility = VISIBLE
                text = (totalItems - displayedItems).toString()
            }
        } else binding.ItemsRemaining.visibility = GONE
    }

    private fun bindList(initializedItems: List<ListItem>, isTitleEmpty: Boolean) {
        binding.apply {
            bindItemsRemaining(initializedItems.size, preferences.maxItems)
            if (initializedItems.isEmpty()) {
                LinearLayout.visibility = GONE
            } else {
                LinearLayout.visibility = VISIBLE
                val forceShowFirstItem = preferences.maxItems < 1 && isTitleEmpty
                val filteredList =
                    initializedItems.take(if (forceShowFirstItem) 1 else preferences.maxItems)
                LinearLayout.children
                    .filterIsInstance(HighlightableTextView::class.java)
                    .forEachIndexed { index, view ->
                        if (index < filteredList.size) {
                            val item = filteredList[index]
                            view.apply {
                                text = item.body
                                handleChecked(this, item.checked)
                                visibility = VISIBLE
                                updateLayoutParams<LinearLayout.LayoutParams> {
                                    marginStart = if (item.isChild) 20.dp else 0
                                }
                                if (index == filteredList.lastIndex) {
                                    updatePadding(bottom = 0)
                                }
                            }
                        } else view.visibility = GONE
                    }
            }
        }
    }

    private fun setColor(color: String) {
        binding.root.apply {
            val colorInt = context.extractColor(color)
            setCardBackgroundColor(colorInt)
            setControlsContrastColorForAllViews(colorInt)
        }
    }

    private fun setImages(images: List<FileAttachment>, mediaRoot: File?) {
        binding.apply {
            if (images.isNotEmpty() && !preferences.hideImages) {
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
