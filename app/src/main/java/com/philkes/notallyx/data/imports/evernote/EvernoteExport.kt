package com.philkes.notallyx.data.imports.evernote

import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.Text
import org.simpleframework.xml.convert.Convert
import org.simpleframework.xml.convert.Converter
import org.simpleframework.xml.stream.InputNode
import org.simpleframework.xml.stream.OutputNode

@Root(name = "en-export", strict = false)
data class EvernoteExport
@JvmOverloads
constructor(
    @field:Attribute(name = "export-date", required = false)
    @param:Attribute(name = "export-date", required = false)
    val exportDate: String = "",
    //    @field:Attribute(name = "application", required = false)
    //    @param:Attribute(name = "application", required = false)
    //    val application: String = "",
    //    @field:Attribute(name = "version", required = false)
    //    @param:Attribute(name = "version", required = false)
    //    val version: String = "",
    @field:ElementList(name = "note", inline = true)
    @param:ElementList(name = "note", inline = true)
    val notes: List<EvernoteNote> = listOf(),
)

@Root(name = "note", strict = false)
data class EvernoteNote
@JvmOverloads
constructor(
    @field:Element(name = "title") @param:Element(name = "title") val title: String = "",
    @field:Element(name = "created") @param:Element(name = "created") val created: String = "",
    @field:Element(name = "updated") @param:Element(name = "updated") val updated: String = "",
    @field:ElementList(name = "tag", inline = true, required = false, empty = false)
    @param:ElementList(name = "tag", inline = true, required = false, empty = false)
    var tag: List<EvernoteTag> = listOf(),
    @field:Element(name = "content", data = true)
    @param:Element(name = "content", data = true)
    val content: String = "",
    @field:ElementList(name = "resource", inline = true, required = false, empty = false)
    @param:ElementList(name = "resource", inline = true, required = false, empty = false)
    val resources: List<EvernoteResource> = listOf(),
    @field:ElementList(name = "task", inline = true, required = false, empty = false)
    @param:ElementList(name = "task", inline = true, required = false, empty = false)
    val tasks: List<EvernoteTask> = listOf(),
)

@Root(name = "tag", strict = false)
data class EvernoteTag
@JvmOverloads
constructor(@field:Text(required = false) @param:Text(required = false) val name: String = "")

@Root(name = "resource", strict = false)
data class EvernoteResource
@JvmOverloads
constructor(
    @field:Element(name = "data", required = false)
    @param:Element(name = "data", required = false)
    val data: EvernoteResourceData? = null,
    @field:Element(name = "mime", required = false)
    @param:Element(name = "mime", required = false)
    val mime: String = "*/*",
    @field:Element(name = "width", required = false)
    @param:Element(name = "width", required = false)
    val width: Int? = null,
    @field:Element(name = "height", required = false)
    @param:Element(name = "height", required = false)
    val height: Int? = null,
    @field:Element(name = "resource-attributes", required = false)
    @param:Element(name = "resource-attributes", required = false)
    val attributes: EvernoteResourceAttributes? = null,
)

@Root(name = "data", strict = false)
data class EvernoteResourceData
@JvmOverloads
constructor(
    @field:Attribute(name = "encoding", required = false)
    @param:Attribute(name = "encoding", required = false)
    val encoding: String = "base64",
    @field:Text(required = false) @param:Text(required = false) val content: String = "",
)

@Root(name = "resource-attributes", strict = false)
data class EvernoteResourceAttributes
@JvmOverloads
constructor(
    @field:Element(name = "file-name", required = false)
    @param:Element(name = "file-name", required = false)
    val fileName: String = ""

    //    @field:Element(name = "source-url", required = false)
    //    @param:Element(name = "source-url", required = false)
    //    val sourceUrl: String= ""
)

@Root(name = "task", strict = false)
data class EvernoteTask
@JvmOverloads
constructor(
    @field:Element(name = "title", required = false)
    @param:Element(name = "title", required = false)
    val title: String = "",
    //
    //    @field:Element(name = "created") @param:Element(name = "created")
    //    val created: String = "",
    //
    //    @field:Element(name = "updated") @param:Element(name = "updated")
    //    val updated: String = "",
    //
    @field:Element(name = "taskStatus", required = false)
    @param:Element(name = "taskStatus", required = false)
    @field:Convert(TaskStatusConverter::class)
    @param:Convert(TaskStatusConverter::class)
    val taskStatus: TaskStatus = TaskStatus.OPEN,
    //
    //    @field:Element(name = "taskFlag") @param:Element(name = "taskFlag")
    //    val taskFlag: Boolean = false,
    //
    @field:Element(name = "sortWeight", required = false)
    @param:Element(name = "sortWeight", required = false)
    val sortWeight: String = "",
    //
    //    @field:Element(name = "noteLevelID") @param:Element(name = "noteLevelID")
    //    val noteLevelID: String = "",
    //
    //    @field:Element(name = "taskGroupNoteLevelID") @param:Element(name =
    // "taskGroupNoteLevelID")
    //    val taskGroupNoteLevelID: String = "",
    //
    //    @field:Element(name = "dueDate") @param:Element(name = "dueDate")
    //    val dueDate: String = "",
    //
    //    @field:Element(name = "statusUpdated") @param:Element(name = "statusUpdated")
    //    val statusUpdated: String = "",
    //
    //    @field:Element(name = "creator") @param:Element(name = "creator")
    //    val creator: String = "",
    //
    //    @field:Element(name = "lastEditor") @param:Element(name = "lastEditor")
    //    val lastEditor: String = ""
)

enum class TaskStatus(val status: String) {
    OPEN("open"),
    COMPLETED("completed");

    companion object {
        fun fromString(value: String): TaskStatus? {
            return entries.find { it.status == value }
        }
    }
}

class TaskStatusConverter : Converter<TaskStatus> {
    override fun read(node: InputNode): TaskStatus? {
        val value = node.value ?: return null
        return TaskStatus.fromString(value)
    }

    override fun write(node: OutputNode, value: TaskStatus?) {
        node.value = value?.status
    }
}
