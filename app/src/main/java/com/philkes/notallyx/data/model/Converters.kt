package com.philkes.notallyx.data.model

import androidx.room.TypeConverter
import java.util.Date
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object Converters {

    // TypeConverter for BaseNote.body compression/decompression
    @TypeConverter
    fun fromBodyString(body: BodyString): String =
        com.philkes.notallyx.utils.CompressUtility.compressIfNeeded(body.value)

    @TypeConverter
    fun toBodyString(dbValue: String): BodyString =
        BodyString(com.philkes.notallyx.utils.CompressUtility.decompressIfNeeded(dbValue))

    @TypeConverter fun labelsToJson(labels: List<String>) = JSONArray(labels).toString()

    @TypeConverter fun jsonToLabels(json: String) = jsonToLabels(JSONArray(json))

    fun jsonToLabels(jsonArray: JSONArray) = jsonArray.iterable<String>().toList()

    @TypeConverter
    fun filesToJson(files: List<FileAttachment>): String {
        val objects =
            files.map { file ->
                val jsonObject = JSONObject()
                jsonObject.put("localName", file.localName)
                jsonObject.put("originalName", file.originalName)
                jsonObject.put("mimeType", file.mimeType)
            }
        return JSONArray(objects).toString()
    }

    @TypeConverter fun jsonToFiles(json: String) = jsonToFiles(JSONArray(json))

    fun jsonToFiles(jsonArray: JSONArray): List<FileAttachment> {
        return jsonArray.iterable<JSONObject>().map { jsonObject ->
            val localName = getSafeLocalName(jsonObject)
            val originalName = getSafeOriginalName(jsonObject)
            val mimeType = jsonObject.getString("mimeType")
            FileAttachment(localName, originalName, mimeType)
        }
    }

    @TypeConverter
    fun audiosToJson(audios: List<Audio>): String {
        val objects =
            audios.map { audio ->
                val jsonObject = JSONObject()
                jsonObject.put("name", audio.name)
                jsonObject.put("duration", audio.duration)
                jsonObject.put("timestamp", audio.timestamp)
            }
        return JSONArray(objects).toString()
    }

    @TypeConverter fun jsonToAudios(json: String) = jsonToAudios(JSONArray(json))

    fun jsonToAudios(json: JSONArray): List<Audio> {
        return json.iterable<JSONObject>().map { jsonObject ->
            val name = jsonObject.getString("name")
            val duration = jsonObject.getSafeLong("duration")
            val timestamp = jsonObject.getLong("timestamp")
            Audio(name, duration, timestamp)
        }
    }

    @TypeConverter fun jsonToSpans(json: String) = jsonToSpans(JSONArray(json))

    fun jsonToSpans(jsonArray: JSONArray): List<SpanRepresentation> {
        return jsonArray
            .iterable<JSONObject>()
            .map { jsonObject ->
                val bold = jsonObject.getSafeBoolean("bold")
                val link = jsonObject.getSafeBoolean("link")
                val linkData = jsonObject.getSafeString("linkData")
                val italic = jsonObject.getSafeBoolean("italic")
                val monospace = jsonObject.getSafeBoolean("monospace")
                val strikethrough = jsonObject.getSafeBoolean("strikethrough")
                try {
                    val start = jsonObject.getInt("start")
                    val end = jsonObject.getInt("end")
                    SpanRepresentation(
                        start,
                        end,
                        bold,
                        link,
                        linkData,
                        italic,
                        monospace,
                        strikethrough,
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .filterNotNull()
    }

    @TypeConverter
    fun spansToJson(list: List<SpanRepresentation>) = spansToJSONArray(list).toString()

    fun spansToJSONArray(list: List<SpanRepresentation>): JSONArray {
        val objects =
            list.map { representation ->
                val jsonObject = JSONObject()
                jsonObject.put("bold", representation.bold)
                jsonObject.put("link", representation.link)
                jsonObject.put("linkData", representation.linkData)
                jsonObject.put("italic", representation.italic)
                jsonObject.put("monospace", representation.monospace)
                jsonObject.put("strikethrough", representation.strikethrough)
                jsonObject.put("start", representation.start)
                jsonObject.put("end", representation.end)
            }
        return JSONArray(objects)
    }

    @TypeConverter fun jsonToItems(json: String) = jsonToItems(JSONArray(json))

    fun jsonToItems(json: JSONArray): List<ListItem> {
        return json.iterable<JSONObject>().map { jsonObject ->
            val body = jsonObject.getSafeString("body") ?: ""
            val checked = jsonObject.getSafeBoolean("checked")
            val isChild = jsonObject.getSafeBoolean("isChild")
            val order = jsonObject.getSafeInt("order")
            ListItem(body, checked, isChild, order, mutableListOf())
        }
    }

    @TypeConverter fun itemsToJson(list: List<ListItem>) = itemsToJSONArray(list).toString()

    fun itemsToJSONArray(list: List<ListItem>): JSONArray {
        val objects =
            list.map { item ->
                val jsonObject = JSONObject()
                jsonObject.put("body", item.body)
                jsonObject.put("checked", item.checked)
                jsonObject.put("isChild", item.isChild)
                jsonObject.put("order", item.order)
            }
        return JSONArray(objects)
    }

    @TypeConverter
    fun remindersToJson(reminders: List<Reminder>) = remindersToJSONArray(reminders).toString()

    fun remindersToJSONArray(reminders: List<Reminder>): JSONArray {
        val objects =
            reminders.map { reminder ->
                JSONObject().apply {
                    put("id", reminder.id) // Store date as long timestamp
                    put("dateTime", reminder.dateTime.time) // Store date as long timestamp
                    put("repetition", reminder.repetition?.let { repetitionToJsonObject(it) })
                }
            }
        return JSONArray(objects)
    }

    @TypeConverter fun jsonToReminders(json: String) = jsonToReminders(JSONArray(json))

    fun jsonToReminders(jsonArray: JSONArray): List<Reminder> {
        return jsonArray.iterable<JSONObject>().map { jsonObject ->
            val id = jsonObject.getLong("id")
            val dateTime = Date(jsonObject.getLong("dateTime"))
            val repetition = jsonObject.getSafeString("repetition")?.let { jsonToRepetition(it) }
            Reminder(id, dateTime, repetition)
        }
    }

    @TypeConverter
    fun repetitionToJson(repetition: Repetition): String {
        return repetitionToJsonObject(repetition).toString()
    }

    fun repetitionToJsonObject(repetition: Repetition): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("value", repetition.value)
        jsonObject.put("unit", repetition.unit.name) // Store the TimeUnit as a string
        return jsonObject
    }

    @TypeConverter
    fun jsonToRepetition(json: String): Repetition {
        val jsonObject = JSONObject(json)
        val value = jsonObject.getInt("value")
        val unit =
            RepetitionTimeUnit.valueOf(
                jsonObject.getString("unit")
            ) // Convert string back to TimeUnit
        return Repetition(value, unit)
    }

    private fun getSafeLocalName(jsonObject: JSONObject): String {
        return try {
            jsonObject.getString("localName")
        } catch (e: JSONException) {
            jsonObject.getString("name")
        }
    }

    private fun getSafeOriginalName(jsonObject: JSONObject): String {
        return try {
            jsonObject.getString("originalName")
        } catch (e: JSONException) {
            getSafeLocalName(jsonObject).substringAfterLast("/")
        }
    }

    private fun JSONObject.getSafeBoolean(name: String): Boolean {
        return try {
            getBoolean(name)
        } catch (exception: JSONException) {
            false
        }
    }

    private fun JSONObject.getSafeString(name: String): String? {
        return try {
            getString(name)
        } catch (exception: JSONException) {
            null
        }
    }

    private fun JSONObject.getSafeInt(name: String): Int? {
        return try {
            getInt(name)
        } catch (exception: JSONException) {
            null
        }
    }

    private fun JSONObject.getSafeLong(name: String): Long? {
        return try {
            getLong(name)
        } catch (exception: JSONException) {
            null
        }
    }

    private fun <T> JSONArray.iterable() = Iterable {
        object : Iterator<T> {
            var index = 0

            override fun next(): T {
                val element = get(index)
                index++
                return element as T
            }

            override fun hasNext(): Boolean {
                return index < length()
            }
        }
    }
}
