package com.philkes.notallyx.data.imports.txt

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Type
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
                    val fileNameWithoutExtension = file.name?.substringBeforeLast(".") ?: ""
                    var content =
                        app.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                reader.readText()
                            }
                        } ?: ""
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
                            color = Color.DEFAULT,
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
}
