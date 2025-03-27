package com.philkes.notallyx.data.imports.txt

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.utils.MIME_TYPE_JSON
import com.philkes.notallyx.utils.readFileContents
import java.io.File

class PlainTextImporter : ExternalImporter {

    override fun import(
        app: Application,
        source: Uri,
        destination: File,
        progress: MutableLiveData<ImportProgress>?,
    ): Pair<List<BaseNote>, File?> {
        val notes = mutableListOf<BaseNote>()
        fun readTxtFiles(file: DocumentFile) {
            when {
                file.isDirectory -> {
                    file.listFiles().forEach { readTxtFiles(it) }
                }

                file.isFile -> {
                    if (file.type?.isTextMimeType() == false) {
                        return
                    }
                    val fileNameWithoutExtension = file.name?.substringBeforeLast(".") ?: ""
                    var content = app.contentResolver.readFileContents(file.uri)
                    val listItems = mutableListOf<ListItem>()
                    content.findListSyntaxRegex()?.let { listSyntaxRegex ->
                        listItems.addAll(content.extractListItems(listSyntaxRegex))
                        content = ""
                    }
                    val timestamp = System.currentTimeMillis()
                    notes.add(
                        BaseNote(
                            id = 0L, // Auto-generated
                            type = if (listItems.isEmpty()) Type.NOTE else Type.LIST,
                            folder = Folder.NOTES,
                            color = BaseNote.COLOR_DEFAULT,
                            title = fileNameWithoutExtension,
                            pinned = false,
                            timestamp = timestamp,
                            modifiedTimestamp = timestamp,
                            labels = listOf(),
                            body = content,
                            spans = listOf(),
                            items = listItems,
                            images = listOf(),
                            files = listOf(),
                            audios = listOf(),
                            reminders = listOf(),
                        )
                    )
                }
            }
        }
        val file =
            if (source.pathSegments.firstOrNull() == "tree") {
                DocumentFile.fromTreeUri(app, source)
            } else DocumentFile.fromSingleUri(app, source)
        file?.let { readTxtFiles(it) }
        return Pair(notes, null)
    }

    private fun String.isTextMimeType(): Boolean {
        return startsWith("text/") || this in APPLICATION_TEXT_MIME_TYPES
    }
}

val APPLICATION_TEXT_MIME_TYPES =
    arrayOf(
        MIME_TYPE_JSON,
        "application/xml",
        "application/javascript",
        "application/xhtml+xml",
        "application/yaml",
    )
