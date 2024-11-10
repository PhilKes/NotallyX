package com.philkes.notallyx.utils.backup

import android.app.Application
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.widget.Toast
import androidx.core.database.getLongOrNull
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.utils.IO.SUBFOLDER_AUDIOS
import com.philkes.notallyx.utils.IO.SUBFOLDER_FILES
import com.philkes.notallyx.utils.IO.SUBFOLDER_IMAGES
import com.philkes.notallyx.utils.IO.clearDirectory
import com.philkes.notallyx.utils.IO.copyToFile
import com.philkes.notallyx.utils.IO.getExternalAudioDirectory
import com.philkes.notallyx.utils.IO.getExternalFilesDirectory
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import com.philkes.notallyx.utils.Operations
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException

object Import {

    /**
     * We only import the images/files referenced in notes. e.g If someone has added garbage to the
     * ZIP file, like a 100 MB image, ignore it.
     */
    suspend fun importZip(
        app: Application,
        zipFileUri: Uri,
        databaseFolder: File,
        zipPassword: String,
        importingBackup: MutableLiveData<ImportProgress>? = null,
    ) {
        importingBackup?.postValue(ImportProgress(indeterminate = true))
        try {
            val importedNotes =
                withContext(Dispatchers.IO) {
                    val stream = requireNotNull(app.contentResolver.openInputStream(zipFileUri))
                    val tempZipFile = File(databaseFolder, "TEMP.zip")
                    stream.copyToFile(tempZipFile)
                    val zipFile = ZipFile(tempZipFile)
                    if (zipFile.isEncrypted) {
                        zipFile.setPassword(zipPassword.toCharArray())
                    }
                    zipFile.extractFile(
                        NotallyDatabase.DatabaseName,
                        databaseFolder.path,
                        NotallyDatabase.DatabaseName,
                    )

                    val database =
                        SQLiteDatabase.openDatabase(
                            File(databaseFolder, NotallyDatabase.DatabaseName).path,
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                        )

                    val labelCursor = database.query("Label", null, null, null, null, null, null)
                    val baseNoteCursor =
                        database.query("BaseNote", null, null, null, null, null, null)

                    val labels = labelCursor.toList { cursor -> cursor.toLabel() }

                    var total = baseNoteCursor.count
                    var counter = 1
                    importingBackup?.postValue(ImportProgress(0, total))
                    val baseNotes =
                        baseNoteCursor.toList { cursor ->
                            val baseNote = cursor.toBaseNote()
                            importingBackup?.postValue(ImportProgress(counter++, total))
                            baseNote
                        }

                    delay(1000)

                    total =
                        baseNotes.fold(0) { acc, baseNote ->
                            acc + baseNote.images.size + baseNote.files.size + baseNote.audios.size
                        }
                    importingBackup?.postValue(
                        ImportProgress(0, total, stage = ImportStage.IMPORT_FILES)
                    )

                    val current = AtomicInteger(1)
                    val imageRoot = app.getExternalImagesDirectory()
                    val fileRoot = app.getExternalFilesDirectory()
                    val audioRoot = app.getExternalAudioDirectory()
                    baseNotes.forEach { baseNote ->
                        importFiles(
                            app,
                            baseNote.images,
                            SUBFOLDER_IMAGES,
                            imageRoot,
                            zipFile,
                            current,
                            total,
                            importingBackup,
                        )
                        importFiles(
                            app,
                            baseNote.files,
                            SUBFOLDER_FILES,
                            fileRoot,
                            zipFile,
                            current,
                            total,
                            importingBackup,
                        )
                        baseNote.audios.forEach { audio ->
                            try {
                                val audioFilePath = "$SUBFOLDER_AUDIOS/${audio.name}"
                                val entry = zipFile.getFileHeader(audioFilePath)
                                if (entry != null) {
                                    val name = "${UUID.randomUUID()}.m4a"
                                    zipFile.extractFile(audioFilePath, audioRoot!!.path, name)
                                    audio.name = name
                                }
                            } catch (exception: Exception) {
                                Operations.log(app, exception)
                            } finally {
                                importingBackup?.postValue(
                                    ImportProgress(
                                        current.getAndIncrement(),
                                        total,
                                        stage = ImportStage.IMPORT_FILES,
                                    )
                                )
                            }
                        }
                    }

                    NotallyDatabase.getDatabase(app, observePreferences = false)
                        .value
                        .getCommonDao()
                        .importBackup(baseNotes, labels)
                    baseNotes.size
                }
            databaseFolder.clearDirectory()
            val message = app.getQuantityString(R.plurals.imported_notes, importedNotes)
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) {
                Toast.makeText(app, R.string.wrong_password, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(app, R.string.invalid_backup, Toast.LENGTH_LONG).show()
                Operations.log(app, e)
            }
        } catch (e: Exception) {
            Toast.makeText(app, R.string.invalid_backup, Toast.LENGTH_LONG).show()
            Operations.log(app, e)
        } finally {
            importingBackup?.value = ImportProgress(inProgress = false)
        }
    }

    private fun importFiles(
        app: Application,
        files: List<FileAttachment>,
        subFolder: String,
        localFolder: File?,
        zipFile: ZipFile,
        current: AtomicInteger,
        total: Int,
        importingBackup: MutableLiveData<ImportProgress>? = null,
    ) {
        files.forEach { file ->
            try {
                val entry = zipFile.getFileHeader("$subFolder/${file.localName}")
                if (entry != null) {
                    val extension = file.localName.substringAfterLast(".")
                    val name = "${UUID.randomUUID()}.$extension"
                    zipFile.extractFile("$subFolder/${file.localName}", localFolder!!.path, name)
                    file.localName = name
                }
            } catch (exception: Exception) {
                Operations.log(app, exception)
            } finally {
                importingBackup?.postValue(
                    ImportProgress(
                        current.getAndIncrement(),
                        total,
                        stage = ImportStage.IMPORT_FILES,
                    )
                )
            }
        }
    }

    private fun Cursor.toLabel(): Label {
        val value = this.getString(getColumnIndexOrThrow("value"))
        return Label(value)
    }

    private fun Cursor.toBaseNote(): BaseNote {
        val typeTmp = getString(getColumnIndexOrThrow("type"))
        val folderTmp = getString(getColumnIndexOrThrow("folder"))
        val colorTmp = getString(getColumnIndexOrThrow("color"))
        val title = getString(getColumnIndexOrThrow("title"))
        val pinnedTmp = getInt(getColumnIndexOrThrow("pinned"))
        val timestamp = getLong(getColumnIndexOrThrow("timestamp"))
        val modifiedTimestampIndex = getColumnIndex("modifiedTimestamp")
        val modifiedTimestamp =
            if (modifiedTimestampIndex == -1) {
                timestamp
            } else {
                getLongOrNull(modifiedTimestampIndex) ?: timestamp
            }
        val labelsTmp = getString(getColumnIndexOrThrow("labels"))
        val body = getString(getColumnIndexOrThrow("body"))
        val spansTmp = getString(getColumnIndexOrThrow("spans"))
        val itemsTmp = getString(getColumnIndexOrThrow("items"))

        val pinned =
            when (pinnedTmp) {
                0 -> false
                1 -> true
                else -> throw IllegalArgumentException("pinned must be 0 or 1")
            }

        val type = Type.valueOf(typeTmp)
        val folder = Folder.valueOf(folderTmp)
        val color = Color.valueOf(colorTmp)

        val labels = Converters.jsonToLabels(labelsTmp)
        val spans = Converters.jsonToSpans(spansTmp)
        val items = Converters.jsonToItems(itemsTmp)

        val imagesIndex = getColumnIndex("images")
        val images =
            if (imagesIndex != -1) {
                Converters.jsonToFiles(getString(imagesIndex))
            } else emptyList()

        val filesIndex = getColumnIndex("files")
        val files =
            if (filesIndex != -1) {
                Converters.jsonToFiles(getString(filesIndex))
            } else emptyList()

        val audiosIndex = getColumnIndex("audios")
        val audios =
            if (audiosIndex != -1) {
                Converters.jsonToAudios(getString(audiosIndex))
            } else emptyList()

        return BaseNote(
            0,
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
        )
    }

    private fun <T> Cursor.toList(convert: (cursor: Cursor) -> T): ArrayList<T> {
        val list = ArrayList<T>(count)
        while (moveToNext()) {
            val item = convert(this)
            list.add(item)
        }
        close()
        return list
    }
}
