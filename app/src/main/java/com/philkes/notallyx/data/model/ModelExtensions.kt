package com.philkes.notallyx.data.model

import android.content.Context
import android.text.Html
import androidx.core.text.toHtml
import com.philkes.notallyx.R
import com.philkes.notallyx.data.dao.NoteIdReminder
import com.philkes.notallyx.data.model.BaseNote.Companion.COLOR_DEFAULT
import com.philkes.notallyx.presentation.applySpans
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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

val FileAttachment.isImage: Boolean
    get() {
        return mimeType.isImageMimeType
    }
val String.isImageMimeType: Boolean
    get() {
        return startsWith("image/")
    }
val String.isAudioMimeType: Boolean
    get() {
        return startsWith("audio/")
    }

fun BaseNote.toTxt(includeTitle: Boolean = true, includeCreationDate: Boolean = true) =
    buildString {
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(timestamp)
        val body =
            when (type) {
                Type.NOTE -> body
                Type.LIST -> items.toText()
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
            .put("color", color)
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
    jsonObject.put("reminders", Converters.remindersToJSONArray(reminders))
    return jsonObject.toString(2)
}

fun String.toBaseNote(): BaseNote {
    val jsonObject = JSONObject(this)
    val id = jsonObject.getLongOrDefault("id", -1L)
    val type = Type.valueOfOrDefault(jsonObject.getStringOrDefault("type", Type.NOTE.name))
    val folder = Folder.valueOfOrDefault(jsonObject.getStringOrDefault("folder", Folder.NOTES.name))
    val color =
        jsonObject.getStringOrDefault("color", COLOR_DEFAULT).takeIf { it.isValid() }
            ?: COLOR_DEFAULT
    val title = jsonObject.getStringOrDefault("title", "")
    val pinned = jsonObject.getBooleanOrDefault("pinned", false)
    val timestamp = jsonObject.getLongOrDefault("timestamp", System.currentTimeMillis())
    val modifiedTimestamp = jsonObject.getLongOrDefault("modifiedTimestamp", timestamp)
    val labels = Converters.jsonToLabels(jsonObject.getArrayOrEmpty("labels"))
    val body = jsonObject.getStringOrDefault("body", "")
    val spans = Converters.jsonToSpans(jsonObject.getArrayOrEmpty("spans"))
    val items = Converters.jsonToItems(jsonObject.getArrayOrEmpty("items"))
    val images = Converters.jsonToFiles(jsonObject.getArrayOrEmpty("images"))
    val files = Converters.jsonToFiles(jsonObject.getArrayOrEmpty("files"))
    val audios = Converters.jsonToAudios(jsonObject.getArrayOrEmpty("audios"))
    val reminders = Converters.jsonToReminders(jsonObject.getArrayOrEmpty("reminders"))
    val viewMode = NoteViewMode.valueOfOrDefault(jsonObject.getStringOrDefault("viewMode", ""))
    return BaseNote(
        id,
        type,
        folder,
        color,
        title,
        pinned,
        timestamp,
        modifiedTimestamp,
        labels,
        body,
        spans,
        items,
        images,
        files,
        audios,
        reminders,
        viewMode,
    )
}

private fun JSONObject.getStringOrDefault(key: String, defaultValue: String): String {
    return try {
        getString(key)
    } catch (exception: JSONException) {
        defaultValue
    }
}

private fun JSONObject.getArrayOrEmpty(key: String): JSONArray {
    return try {
        getJSONArray(key)
    } catch (exception: JSONException) {
        JSONArray("[]")
    }
}

private fun JSONObject.getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
    return try {
        getBoolean(key)
    } catch (exception: JSONException) {
        defaultValue
    }
}

private fun JSONObject.getLongOrDefault(key: String, defaultValue: Long): Long {
    return try {
        getLong(key)
    } catch (exception: JSONException) {
        defaultValue
    }
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

fun List<BaseNote>.toNoteIdReminders() = map { NoteIdReminder(it.id, it.reminders) }

fun BaseNote.attachmentsDifferFrom(other: BaseNote): Boolean {
    return files.size != other.files.size ||
        files.any { file -> other.files.none { it.localName == file.localName } } ||
        other.files.any { file -> files.none { it.localName == file.localName } } ||
        images.any { image -> other.images.none { it.localName == image.localName } } ||
        other.images.any { image -> images.none { it.localName == image.localName } } ||
        audios.any { audio -> other.audios.none { it.name == audio.name } } ||
        other.audios.any { audio -> audios.none { it.name == audio.name } }
}

fun Date.toText(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(this)

fun Repetition.toText(context: Context): String =
    when {
        value == 1 && unit == RepetitionTimeUnit.DAYS -> context.getString(R.string.daily)
        value == 1 && unit == RepetitionTimeUnit.WEEKS -> context.getString(R.string.weekly)
        value == 1 && unit == RepetitionTimeUnit.MONTHS -> context.getString(R.string.monthly)
        value == 1 && unit == RepetitionTimeUnit.YEARS -> context.getString(R.string.yearly)
        else -> "${context.getString(R.string.every)} $value ${unit.toText(context)}"
    }

private fun RepetitionTimeUnit.toText(context: Context): String {
    val resId =
        when (this) {
            RepetitionTimeUnit.MINUTES -> R.string.minutes
            RepetitionTimeUnit.HOURS -> R.string.hours
            RepetitionTimeUnit.DAYS -> R.string.days
            RepetitionTimeUnit.WEEKS -> R.string.weeks
            RepetitionTimeUnit.MONTHS -> R.string.months
            RepetitionTimeUnit.YEARS -> R.string.years
        }
    return context.getString(resId)
}

fun Collection<Reminder>.copy() = map { it.copy() }

fun RepetitionTimeUnit.toCalendarField(): Int {
    return when (this) {
        RepetitionTimeUnit.MINUTES -> Calendar.MINUTE
        RepetitionTimeUnit.HOURS -> Calendar.HOUR
        RepetitionTimeUnit.DAYS -> Calendar.DAY_OF_MONTH
        RepetitionTimeUnit.WEEKS -> Calendar.WEEK_OF_YEAR
        RepetitionTimeUnit.MONTHS -> Calendar.MONTH
        RepetitionTimeUnit.YEARS -> Calendar.YEAR
    }
}

fun Reminder.nextNotification(from: Date = Date()): Date? {
    if (from.before(dateTime)) {
        return dateTime
    }
    if (repetition == null) {
        return null
    }
    val timeDifferenceMillis: Long = from.time - dateTime.time
    val intervalsPassed = timeDifferenceMillis / repetition!!.toMillis()
    val unitsUntilNext = ((repetition!!.value) * (intervalsPassed + 1)).toInt()
    val reminderStart = dateTime.toCalendar()
    reminderStart.add(repetition!!.unit.toCalendarField(), unitsUntilNext)
    return reminderStart.time
}

fun Reminder.nextRepetition(from: Date = Date()): Date? {
    if (repetition == null) {
        return null
    }
    return nextNotification(from)
}

fun Reminder.hasUpcomingNotification() = !(dateTime.before(Date()) && repetition == null)

fun Repetition.toMillis(): Long {
    return Calendar.getInstance()
        .apply {
            timeInMillis = 0
            add(unit.toCalendarField(), value)
        }
        .timeInMillis
}

fun Collection<Reminder>.hasAnyUpcomingNotifications(): Boolean {
    return any { it.hasUpcomingNotification() }
}

fun Collection<Reminder>.findNextNotificationDate(): Date? {
    return mapNotNull { it.nextNotification() }.minByOrNull { it }
}

fun Date.toCalendar() = Calendar.getInstance().apply { timeInMillis = this@toCalendar.time }

fun List<ListItem>.toText() = buildString {
    for (item in this@toText) {
        val check = if (item.checked) "[✓]" else "[ ]"
        val childIndentation = if (item.isChild) "    " else ""
        appendLine("$childIndentation$check ${item.body}")
    }
}

fun Collection<ListItem>.deepCopy() = map { it.copy(children = mutableListOf()) }

fun ColorString.isValid() =
    when (this) {
        COLOR_DEFAULT -> true
        else ->
            try {
                android.graphics.Color.parseColor(this)
                true
            } catch (e: Exception) {
                false
            }
    }
