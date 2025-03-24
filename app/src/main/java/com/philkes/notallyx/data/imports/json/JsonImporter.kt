package com.philkes.notallyx.data.imports.txt

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.imports.ExternalImporter
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.toBaseNote
import com.philkes.notallyx.utils.MIME_TYPE_JSON
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class JsonImporter : ExternalImporter {

    override fun import(
        app: Application,
        source: Uri,
        destination: File,
        progress: MutableLiveData<ImportProgress>?,
    ): Pair<List<BaseNote>, File?> {
        val notes = mutableListOf<BaseNote>()
        fun readJsonFiles(file: DocumentFile) {
            when {
                file.isDirectory -> {
                    file.listFiles().forEach { readJsonFiles(it) }
                }
                file.isFile -> {
                    if (file.type != MIME_TYPE_JSON) {
                        return
                    }
                    val fileNameWithoutExtension = file.name?.substringBeforeLast(".") ?: ""
                    val content =
                        app.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                reader.readText()
                            }
                        } ?: ""
                    notes.add(content.toBaseNote().copy(id = 0L, title = fileNameWithoutExtension))
                }
            }
        }
        val file =
            if (source.pathSegments.firstOrNull() == "tree") {
                DocumentFile.fromTreeUri(app, source)
            } else DocumentFile.fromSingleUri(app, source)
        file?.let { readJsonFiles(it) }
        return Pair(notes, null)
    }
}
