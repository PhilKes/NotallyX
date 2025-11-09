package com.philkes.notallyx.presentation.view.misc.highlightableview

import android.content.Context
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible

/** Amount of lines to show for a search snippet on top and below the keyword line */
const val SEARCH_SNIPPET_ITEM_LINES = 2
const val SEARCH_SNIPPET_SIZE = 100
private const val ELLIPSED_TEXT = "..."

/** TextView that can highlight keyword with snippet around it */
class HighlightableTextView(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    fun clearHighlights() {
        text = text.toString()
    }

    /** Visibly highlight text from [startIdx] to [endIdx]. */
    fun highlight(keyword: String) {
        val startIdx = this.text.indexOf(keyword, ignoreCase = true)
        if (startIdx == -1) {
            return
        }
        highlight(startIdx, startIdx + keyword.length)
    }

    fun highlight(startIdx: Int, endIdx: Int) {
        this.text =
            SpannableString(this.text.toString()).apply {
                setSpan(HighlightSpan(highlightColor), startIdx, endIdx, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
    }

    fun showSearchSnippet(snippet: Snippet) {
        val displayedText = buildString {
            if (!snippet.includesFirstLine) append(ELLIPSED_TEXT)
            append(snippet.text)
            if (!snippet.includesLastLine) append(ELLIPSED_TEXT)
        }
        val displayKeywordIdx =
            when {
                !snippet.includesFirstLine -> snippet.keywordIdx + ELLIPSED_TEXT.length
                else -> snippet.keywordIdx
            }
        text = displayedText
        highlight(displayKeywordIdx, displayKeywordIdx + snippet.keyword.length)
        isVisible = true
        maxLines = Int.MAX_VALUE
    }

    /**
     * Extract a snippet around given keyword that has given lines around the keyword.
     *
     * @return Snippet's text, keyword idx in the Snippet, whether or not the snippet includes the
     *   first line of the text.
     */
    fun extractSearchSnippet(text: String, keyword: String): Snippet? {
        val keywordIndex = text.indexOf(keyword, ignoreCase = true)
        if (keywordIndex == -1) {
            return null
        }
        if (text.length <= (2 * SEARCH_SNIPPET_SIZE) + keyword.length) {
            return Snippet(text, keyword, text.indexOf(keyword, ignoreCase = true), true, true)
        }
        val start = (keywordIndex - SEARCH_SNIPPET_SIZE).coerceAtLeast(0)
        val end = (keywordIndex + keyword.length + SEARCH_SNIPPET_SIZE).coerceAtMost(text.lastIndex)
        val snippetText = text.substring(start, end + 1)
        val keywordIdxInSnippet = snippetText.indexOf(keyword, ignoreCase = true)
        return Snippet(snippetText, keyword, keywordIdxInSnippet, start == 0, end == text.lastIndex)
    }

    fun TextView.ellipsizeMultilineTextToFit(text: String): String {
        val paint = paint
        val availableWidth = width - paddingLeft - paddingRight
        //        if (availableWidth <= 0) {
        //            // Wait for layout if width not ready yet
        //            post { ellipsizeMultilineTextToFit(text) }
        //            return text
        //        }

        return text.lines().joinToString("\n") { line ->
            TextUtils.ellipsize(line, paint, availableWidth.toFloat(), TextUtils.TruncateAt.END)
                .toString()
        }
    }
}

data class Snippet(
    val text: String,
    val keyword: String,
    val keywordIdx: Int,
    val includesFirstLine: Boolean,
    val includesLastLine: Boolean,
)
