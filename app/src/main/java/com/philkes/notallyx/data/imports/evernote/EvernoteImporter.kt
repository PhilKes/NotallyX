package com.philkes.notallyx.data.imports.evernote

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.data.imports.evernote.EvernoteImporter.Companion.parseTimestamp
import com.philkes.notallyx.data.imports.parseBodyAndSpansFromHtml
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.utils.IO.write
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.simpleframework.xml.Serializer
import org.simpleframework.xml.convert.AnnotationStrategy
import org.simpleframework.xml.core.Persister

class EvernoteImporter : ExternalImporter {
    private val serializer: Serializer = Persister(AnnotationStrategy())

    override fun importFrom(uri: Uri, app: Application): Pair<List<BaseNote>, File> {
        if (MimeTypeMap.getFileExtensionFromUrl(uri.toString()) != "enex") {
            throw ImportException(R.string.invalid_evernote)
        }
        val tempDir = File(app.cacheDir, ImportSource.EVERNOTE.folderName)
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val evernoteExport: EvernoteExport =
            parseExport(app.contentResolver.openInputStream(uri)!!)!!
        saveResourcesToFiles(
            evernoteExport.notes.flatMap { it.resources }.distinctBy { it.attributes?.fileName },
            tempDir,
        )
        try {
            val notes = evernoteExport.notes.map { it.mapToBaseNote() }
            return Pair(notes, tempDir)
        } catch (e: Exception) {
            throw ImportException(R.string.invalid_evernote)
        }
    }

    fun parseExport(inputStream: InputStream): EvernoteExport? =
        try {
            serializer.read(EvernoteExport::class.java, inputStream)
        } catch (e: Exception) {
            throw ImportException(R.string.invalid_evernote, e)
        }

    private fun saveResourcesToFiles(resources: Collection<EvernoteResource>, dir: File) {
        resources.forEach {
            val file = File(dir, it.attributes!!.fileName)
            val data = Base64.decode(it.data!!.content.trimStart(), Base64.DEFAULT)
            file.write(data)
        }
    }

    companion object {
        fun parseTimestamp(timestamp: String): Long {
            val format = SimpleDateFormat(EVERNOTE_DATE_FORMAT, Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            return try {
                val date: Date = format.parse(timestamp) ?: Date()
                date.time // Return milliseconds since epoch
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}

private const val EVERNOTE_DATE_FORMAT = "yyyyMMdd'T'HHmmss'Z'"
private const val EVERNOTE_NOTE_XML_TAG = "en-note"

fun EvernoteNote.mapToBaseNote(): BaseNote {
    val (body, spans) =
        parseBodyAndSpansFromHtml(
            content,
            EVERNOTE_NOTE_XML_TAG,
            useInnermostRootTag = true,
            brTagsAsNewLine = false,
        )
    val images = resources.filterByMimeTypePrefix("image").toFileAttachments()
    val files = resources.filterByExcludedMimeTypePrefixes("image", "audio").toFileAttachments()
    val audios =
        resources.filterByMimeTypePrefix("audio").map {
            Audio(it.attributes!!.fileName, -1, System.currentTimeMillis())
        }

    return BaseNote(
        0L,
        type = if (tasks.isEmpty()) Type.NOTE else Type.LIST,
        folder = Folder.NOTES, // There is no archive in Evernote, also deleted notes are not
        // exported
        color = Color.DEFAULT, // TODO: possible in Evernote?
        title = title,
        pinned = false, // not exported from Evernote
        timestamp = parseTimestamp(created),
        modifiedTimestamp = parseTimestamp(updated),
        labels = tag.map { it.name },
        body = body,
        spans = spans,
        items = tasks.mapToListItem(),
        images = images,
        files = files,
        audios = audios,
    )
}

fun Collection<EvernoteResource>.filterByMimeTypePrefix(
    mimeTypePrefix: String
): List<EvernoteResource> {
    return filter { it.mime.startsWith(mimeTypePrefix) }
}

fun Collection<EvernoteResource>.filterByExcludedMimeTypePrefixes(
    vararg mimeTypePrefix: String
): List<EvernoteResource> {
    return filter { !it.mime.startsWithAnyOf(*mimeTypePrefix) }
}

private fun String.startsWithAnyOf(vararg s: String): Boolean {
    s.forEach { if (startsWith(it)) return true }
    return false
}

fun Collection<EvernoteResource>.toFileAttachments(): List<FileAttachment> {
    return map { FileAttachment(it.attributes!!.fileName, it.attributes.fileName, it.mime) }
}

fun Collection<EvernoteTask>.mapToListItem(): List<ListItem> {
    return sortedBy { it.sortWeight }
        .mapIndexed { index, evernoteTask ->
            ListItem(
                body = evernoteTask.title,
                checked = evernoteTask.taskStatus == TaskStatus.COMPLETED,
                isChild = false, // You cant indent tasks in Evernote
                order = index,
                children = mutableListOf(),
            )
        }
}
