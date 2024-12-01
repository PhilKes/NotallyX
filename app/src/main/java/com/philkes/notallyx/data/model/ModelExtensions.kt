package com.philkes.notallyx.data.model

import android.text.Html
import android.util.Patterns
import androidx.core.text.toHtml
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.utils.Operations
import java.text.DateFormat
import org.json.JSONArray
import org.json.JSONObject

fun CharSequence?.isWebUrl(): Boolean {
    return this?.let { Patterns.WEB_URL.matcher(this).matches() } ?: false
}

private const val NOTE_URL_PREFIX = "note://"
private val NOTE_URL_POSTFIX_NOTE = "/${Type.NOTE.name}"
private val NOTE_URL_POSTFIX_LIST = "/${Type.LIST.name}"

fun CharSequence?.isNoteUrl(): Boolean {
    return this?.let { startsWith(NOTE_URL_PREFIX) } ?: false
}

fun Long.createNoteUrl(type: Type): String {
    val postfix =
        when (type) {
            Type.LIST -> NOTE_URL_POSTFIX_LIST
            Type.NOTE -> NOTE_URL_POSTFIX_NOTE
        }
    return "$NOTE_URL_PREFIX$this$postfix"
}

fun String.getNoteIdFromUrl(): Long {
    return substringAfter(NOTE_URL_PREFIX).substringBefore("/").toLong()
}

fun String.getNoteTypeFromUrl(): Type {
    return Type.valueOf(substringAfterLast("/"))
}

fun String.getUrl(start: Int, end: Int): String {
    return if (end <= length) {
        substring(start, end).toUrl()
    } else substring(start, length).toUrl()
}

private fun String.toUrl(): String {
    return when {
        matches(Patterns.PHONE.toRegex()) -> "tel:$this"
        matches(Patterns.EMAIL_ADDRESS.toRegex()) -> "mailto:$this"
        matches(Patterns.DOMAIN_NAME.toRegex()) -> "http://$this"
        else -> this
    }
}

val FileAttachment.isImage: Boolean
    get() {
        return mimeType.startsWith("image/")
    }

val String.toPreservedByteArray: ByteArray
    get() {
        return this.toByteArray(Charsets.ISO_8859_1)
    }

val ByteArray.toPreservedString: String
    get() {
        return String(this, Charsets.ISO_8859_1)
    }

fun BaseNote.toTxt(includeTitle: Boolean = true, includeCreationDate: Boolean = true) =
    buildString {
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(timestamp)
        val body =
            when (type) {
                Type.NOTE -> body
                Type.LIST -> Operations.getBody(items)
            }

        if (title.isNotEmpty() && includeTitle) {
            append("${title}\n\n")
        }
        if (includeCreationDate) {
            append("$date\n\n")
        }
        append(body)
        return toString()
    }

fun BaseNote.toJson(): String {
    val jsonObject =
        JSONObject()
            .put("type", type.name)
            .put("color", color.name)
            .put("title", title)
            .put("pinned", pinned)
            .put("date-created", timestamp)
            .put("labels", JSONArray(labels))

    when (type) {
        Type.NOTE -> {
            jsonObject.put("body", body)
            jsonObject.put("spans", Converters.spansToJSONArray(spans))
        }

        Type.LIST -> {
            jsonObject.put("items", Converters.itemsToJSONArray(items))
        }
    }

    return jsonObject.toString(2)
}

fun BaseNote.toHtml(showDateCreated: Boolean) = buildString {
    val date = DateFormat.getDateInstance(DateFormat.FULL).format(timestamp)
    val title = Html.escapeHtml(title)

    append("<!DOCTYPE html>")
    append("<html><head>")
    append("<meta charset=\"UTF-8\"><title>$title</title>")
    append("</head><body>")
    append("<h2>$title</h2>")

    if (showDateCreated) {
        append("<p>$date</p>")
    }

    when (type) {
        Type.NOTE -> {
            val body = body.applySpans(spans).toHtml()
            append(body)
        }

        Type.LIST -> {
            append("<ol style=\"list-style: none; padding: 0;\">")
            items.forEach { item ->
                val body = Html.escapeHtml(item.body)
                val checked = if (item.checked) "checked" else ""
                val child = if (item.isChild) "style=\"margin-left: 20px\"" else ""
                append("<li><input type=\"checkbox\" $child $checked>$body</li>")
            }
            append("</ol>")
        }
    }
    append("</body></html>")
}
