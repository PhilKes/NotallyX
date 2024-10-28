package com.philkes.notallyx.data.model

import android.util.Patterns

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
