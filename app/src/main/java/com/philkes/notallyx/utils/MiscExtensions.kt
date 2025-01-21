package com.philkes.notallyx.utils

import android.util.Patterns
import java.util.Locale

fun CharSequence.truncate(limit: Int): CharSequence {
    return if (length > limit) {
        val truncated = take(limit)
        val remainingCharacters = length - limit
        "$truncated... ($remainingCharacters more characters)"
    } else {
        this
    }
}

fun CharSequence.startsWithAnyOf(vararg s: String): Boolean {
    s.forEach { if (startsWith(it)) return true }
    return false
}

fun CharSequence.fromCamelCaseToEnumName(): String {
    return this.fold(StringBuilder()) { acc, char ->
            if (char.isUpperCase() && acc.isNotEmpty()) {
                acc.append("_")
            }
            acc.append(char.uppercase())
        }
        .toString()
}

fun CharSequence?.isWebUrl(): Boolean {
    return this?.let { Patterns.WEB_URL.matcher(this).matches() } ?: false
}

fun CharSequence?.findWebUrls(): Collection<Pair<Int, Int>> {
    return this?.let {
        val matcher = Patterns.WEB_URL.matcher(this)
        val matches = mutableListOf<Pair<Int, Int>>()
        while (matcher.find()) {
            matches.add(Pair(matcher.start(), matcher.end()))
        }
        matches
    } ?: listOf()
}

fun String.findAllOccurrences(
    search: String,
    caseSensitive: Boolean = false,
): List<Pair<Int, Int>> {
    if (search.isEmpty()) return emptyList()
    val regex = Regex(Regex.escape(if (caseSensitive) search else search.lowercase()))
    return regex
        .findAll(if (caseSensitive) this else this.lowercase())
        .map { match -> match.range.first to match.range.last + 1 }
        .toList()
}

fun String.removeTrailingParentheses(): String {
    return substringBeforeLast(" (")
}

fun String.toCamelCase(): String {
    return this.lowercase()
        .split("_")
        .mapIndexed { index, word ->
            if (index == 0) word
            else
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
        }
        .joinToString("")
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

val String.toPreservedByteArray: ByteArray
    get() {
        return this.toByteArray(Charsets.ISO_8859_1)
    }

val ByteArray.toPreservedString: String
    get() {
        return String(this, Charsets.ISO_8859_1)
    }
