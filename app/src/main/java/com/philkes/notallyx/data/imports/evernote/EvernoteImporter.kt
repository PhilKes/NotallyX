package com.philkes.notallyx.data.imports.evernote

import android.app.Application
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.imports.evernote.EvernoteImporter.Companion.parseTimestamp
import com.philkes.notallyx.data.imports.parseBodyAndSpansFromHtml
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.startsWithAnyOf
import com.philkes.notallyx.utils.write
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

    override fun import(
        app: Application,
        source: Uri,
        destination: File,
        progress: MutableLiveData<ImportProgress>?,
    ): Pair<List<BaseNote>, File> {
        progress?.postValue(ImportProgress(indeterminate = true))
        if (MimeTypeMap.getFileExtensionFromUrl(source.toString()) != "enex") {
            throw ImportException(
                R.string.invalid_evernote,
                IllegalArgumentException("Provided file is not in ENEX format"),
            )
        }
        val evernoteExport: EvernoteExport =
            parseExport(app.contentResolver.openInputStream(source)!!)!!

        val total = evernoteExport.notes.size
        progress?.postValue(ImportProgress(total = total))
        var counter = 1
        try {
            val notes =
                evernoteExport.notes.map {
                    val note = it.mapToBaseNote()
                    progress?.postValue(ImportProgress(current = counter++, total = total))
                    note
                }
            val resources =
                evernoteExport.notes.flatMap { it.resources }.distinctBy { it.attributes?.fileName }
            saveResourcesToFiles(app, resources, destination, progress)
            return Pair(notes, destination)
        } catch (e: Exception) {
            throw ImportException(R.string.invalid_evernote, e)
        }
    }

    fun parseExport(inputStream: InputStream): EvernoteExport? =
        try {
            serializer.read(EvernoteExport::class.java, inputStream)
        } catch (e: Exception) {
            throw ImportException(R.string.invalid_evernote, e)
        }

    private fun saveResourcesToFiles(
        app: Application,
        resources: Collection<EvernoteResource>,
        dir: File,
        progress: MutableLiveData<ImportProgress>? = null,
    ) {
        progress?.postValue(
            ImportProgress(total = resources.size, stage = ImportStage.EXTRACT_FILES)
        )
        resources.forEachIndexed { idx, it ->
            val file = File(dir, it.attributes!!.fileName)
            try {
                val data = Base64.decode(it.data!!.content.trimStart(), Base64.DEFAULT)
                file.write(data)
            } catch (e: Exception) {
                app.log(TAG, throwable = e)
            }
            progress?.postValue(
                ImportProgress(
                    current = idx + 1,
                    total = resources.size,
                    stage = ImportStage.EXTRACT_FILES,
                )
            )
        }
    }

    companion object {
        private const val TAG = "EvernoteImporter"

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
