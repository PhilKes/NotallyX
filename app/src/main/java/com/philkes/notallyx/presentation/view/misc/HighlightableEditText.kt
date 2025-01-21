package com.philkes.notallyx.presentation.view.misc

import android.content.Context
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatEditText
import com.philkes.notallyx.presentation.removeSelectionFromSpans
import com.philkes.notallyx.presentation.withAlpha

/**
 * [AppCompatEditText] whose changes (text edits or span changes) are pushed to [changeHistory].
 * *
 */
open class HighlightableEditText(context: Context, attrs: AttributeSet) :
    EditTextWithWatcher(context, attrs) {

    fun getSpanRange(span: CharacterStyle): Pair<Int, Int> {
        val text = super.getText()!!
        return Pair(text.getSpanStart(span), text.getSpanEnd(span))
    }

    /**
     * Removes [span] from `text`.
     *
     * @param removeText if this is `true` the text of the [span] is removed from `text`.
     */
    protected fun removeSpan(span: CharacterStyle, removeText: Boolean = false) {
        val (start, end) = getSpanRange(span)
        text?.removeSelectionFromSpans(start, end)
        if (removeText) {
            text?.delete(start, end)
        }
    }

    protected fun applySpan(
        span: CharacterStyle,
        start: Int = selectionStart,
        end: Int = selectionEnd,
    ) {
        text?.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private val highlightedSpans: MutableList<CharacterStyle> = mutableListOf()
    private var selectedHighlightedSpan: CharacterStyle? = null

    fun clearHighlights() {
        highlightedSpans.apply {
            forEach { span -> removeSpan(span) }
            clear()
        }
        selectedHighlightedSpan = null
    }

    /**
     * Visibly highlight text from [startIdx] to [endIdx]. If [selected] is true the text is
     * highlighted uniquely. There can only be one [selected] highlight.
     *
     * @return Vertical offset to highlighted text line.
     */
    fun highlight(startIdx: Int, endIdx: Int, selected: Boolean): Int? {
        // TODO: Could be replaced with EditText.highlights? (API >= 34)
        if (selected) {
            selectedHighlightedSpan?.unselect()
        }
        highlightedSpans
            .filter { getSpanRange(it) == Pair(startIdx, endIdx) }
            .forEach {
                removeSpan(it)
                highlightedSpans.remove(it)
            }
        val span = HighlightSpan(if (selected) highlightColor else highlightColor.withAlpha(0.1f))
        applySpan(span, startIdx, endIdx)
        highlightedSpans.add(span)
        if (selected) {
            selectedHighlightedSpan = span
        }
        return layout?.let {
            val line = layout.getLineForOffset(startIdx)
            layout.getLineTop(line)
        }
    }

    fun unselectHighlight() {
        selectedHighlightedSpan?.unselect()
    }

    private fun CharacterStyle.unselect() {
        val (previousHighlightedStartIdx, previousHighlightedEndIdx) = getSpanRange(this)
        if (previousHighlightedStartIdx != -1) {
            removeSpan(this)
            highlight(previousHighlightedStartIdx, previousHighlightedEndIdx, false)
        }
    }
}

class HighlightSpan(@ColorInt color: Int) : BackgroundColorSpan(color)
