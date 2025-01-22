package com.philkes.notallyx.utils.backup

import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

fun File.toBaseNote(folder: Folder): BaseNote {
    val inputStream = FileInputStream(this)
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(inputStream, null)
    parser.next()
    return parser.parseBaseNote(parser.name, folder)
}

fun InputStream.readAsBackup(): Pair<List<BaseNote>, List<Label>> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(this, null)

    val baseNotes = ArrayList<BaseNote>()
    val labels = ArrayList<Label>()

    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            when (parser.name) {
                "notes" -> parser.parseBaseNoteList(parser.name, baseNotes, Folder.NOTES)
                "deleted-notes" -> parser.parseBaseNoteList(parser.name, baseNotes, Folder.DELETED)
                "archived-notes" ->
                    parser.parseBaseNoteList(parser.name, baseNotes, Folder.ARCHIVED)
                "label" -> labels.add(Label(parser.nextText()))
            }
        }
    }

    return Pair(baseNotes, labels)
}

private fun XmlPullParser.parseBaseNoteList(
    rootTag: String,
    list: ArrayList<BaseNote>,
    folder: Folder,
) {
    while (next() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            val note = this.parseBaseNote(name, folder)
            list.add(note)
        } else if (eventType == XmlPullParser.END_TAG) {
            if (name == rootTag) {
                break
            }
        }
    }
}

private fun XmlPullParser.parseBaseNote(rootTag: String, folder: Folder): BaseNote {
    var color = Color.DEFAULT

    var body = String()
    var title = String()
    var timestamp = 0L
    var pinned = false
    val items = ArrayList<ListItem>()

    val labels = ArrayList<String>()
    val spans = ArrayList<SpanRepresentation>()

    while (next() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            when (name) {
                "color" -> color = Color.valueOf(nextText())
                "title" -> title = nextText()
                "body" -> body = nextText()
                "date-created" -> timestamp = nextText().toLong()
                "pinned" -> pinned = nextText().toBoolean()
                "label" -> labels.add(nextText())
                "item" -> items.add(this.parseListItem(name))
                "span" -> spans.add(this.parseSpan())
            }
        } else if (eventType == XmlPullParser.END_TAG) {
            if (name == rootTag) {
                break
            }
        }
    }

    // Can be either `note` or `list`
    val type =
        if (rootTag == "note") {
            Type.NOTE
        } else Type.LIST
    return BaseNote(
        0,
        type,
        folder,
        color,
        title,
        pinned,
        timestamp,
        timestamp,
        labels,
        body,
        spans,
        items,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )
}

private fun XmlPullParser.parseListItem(rootTag: String): ListItem {
    var body = String()
    var checked = false
    var isChild = false
    var order: Int? = null

    while (next() != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG) {
            when (name) {
                "text" -> body = nextText()
                "checked" -> checked = nextText()?.toBoolean() ?: false
                "isChild" -> isChild = nextText()?.toBoolean() ?: false
                "order" -> order = nextText()?.toInt()
            }
        } else if (eventType == XmlPullParser.END_TAG) {
            if (name == rootTag) {
                break
            }
        }
    }

    return ListItem(body, checked, isChild, order, mutableListOf())
}

private fun XmlPullParser.parseSpan(): SpanRepresentation {
    val start = getAttributeValue(null, "start").toInt()
    val end = getAttributeValue(null, "end").toInt()
    val bold = getAttributeValue(null, "bold")?.toBoolean() ?: false
    val link = getAttributeValue(null, "link")?.toBoolean() ?: false
    val linkData = getAttributeValue(null, "linkData")?.toString()
    val italic = getAttributeValue(null, "italic")?.toBoolean() ?: false
    val monospace = getAttributeValue(null, "monospace")?.toBoolean() ?: false
    val strikethrough = getAttributeValue(null, "strike")?.toBoolean() ?: false
    return SpanRepresentation(start, end, bold, link, linkData, italic, monospace, strikethrough)
}
