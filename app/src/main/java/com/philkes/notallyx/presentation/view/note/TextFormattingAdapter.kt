package com.philkes.notallyx.presentation.view.note

import android.content.Context
import android.graphics.Typeface
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.StylableEditTextWithHistory

class TextFormattingAdapter(context: Context, private val editText: StylableEditTextWithHistory) :
    ToggleAdapter(mutableListOf()) {

    private var link: Toggle =
        Toggle(R.string.link, R.drawable.link, false) {
            if (!it.checked) {
                editText.showAddLinkDialog(
                    context = context,
                    onClose = { updateTextFormattingToggles() },
                )
            } else {
                val spans = editText.getSpans(type = StylableEditTextWithHistory.TextStyleType.LINK)
                spans.firstOrNull()?.let { urlSpan ->
                    editText.showEditDialog((urlSpan as URLSpan)) { updateTextFormattingToggles() }
                }
            }
            it.checked = !it.checked
            notifyItemChanged(0)
        }
    private val bold: Toggle =
        Toggle(R.string.bold, R.drawable.format_bold, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.BOLD))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.BOLD)
            }
            it.checked = !it.checked
            notifyItemChanged(1)
        }
    private val italic: Toggle =
        Toggle(R.string.italic, R.drawable.format_italic, false) {
            if (!it.checked) {
                editText.applySpan(StyleSpan(Typeface.ITALIC))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.ITALIC)
            }
            it.checked = !it.checked
            notifyItemChanged(2)
        }
    private val strikethrough: Toggle =
        Toggle(R.string.strikethrough, R.drawable.format_strikethrough, false) {
            if (!it.checked) {
                editText.applySpan(StrikethroughSpan())
            } else {
                editText.clearFormatting(
                    type = StylableEditTextWithHistory.TextStyleType.STRIKETHROUGH
                )
            }
            it.checked = !it.checked
            notifyItemChanged(3)
        }
    private val monospace: Toggle =
        Toggle(R.string.monospace, R.drawable.code, false) {
            if (!it.checked) {
                editText.applySpan(TypefaceSpan("monospace"))
            } else {
                editText.clearFormatting(type = StylableEditTextWithHistory.TextStyleType.MONOSPACE)
            }
            it.checked = !it.checked
            notifyItemChanged(4)
        }
    private val clearFormat: Toggle =
        Toggle(R.string.clear_formatting, R.drawable.format_clear, false) {
            editText.clearFormatting()
            updateTextFormattingToggles()
        }

    init {
        toggles.addAll(listOf(link, bold, italic, strikethrough, monospace, clearFormat))
    }

    internal fun updateTextFormattingToggles(
        selStart: Int = editText.selectionStart,
        selEnd: Int = editText.selectionEnd,
    ) {
        var boldSpanFound = false
        var italicSpanFound = false
        var linkSpanFound = false
        var monospaceSpanFound = false
        var strikethroughSpanFound = false
        editText.getSpans(selStart, selEnd).forEach { span ->
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> boldSpanFound = true
                        Typeface.ITALIC -> italicSpanFound = true
                    }
                }

                is URLSpan -> linkSpanFound = true
                is TypefaceSpan -> if (span.family == "monospace") monospaceSpanFound = true
                is StrikethroughSpan -> strikethroughSpanFound = true
            }
        }
        bold.checked = boldSpanFound
        italic.checked = italicSpanFound
        link.checked = linkSpanFound
        monospace.checked = monospaceSpanFound
        strikethrough.checked = strikethroughSpanFound
        notifyDataSetChanged()
    }
}
