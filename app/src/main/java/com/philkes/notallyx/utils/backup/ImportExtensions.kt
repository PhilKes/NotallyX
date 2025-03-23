package com.philkes.notallyx.utils.backup

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportStage
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.parseToColorString
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.viewmodel.NotallyModel.FileType
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.SUBFOLDER_AUDIOS
import com.philkes.notallyx.utils.SUBFOLDER_FILES
import com.philkes.notallyx.utils.SUBFOLDER_IMAGES
import com.philkes.notallyx.utils.cancelNoteReminders
import com.philkes.notallyx.utils.clearDirectory
import com.philkes.notallyx.utils.copyToFile
import com.philkes.notallyx.utils.determineMimeTypeAndExtension
import com.philkes.notallyx.utils.getExternalAudioDirectory
import com.philkes.notallyx.utils.getExternalFilesDirectory
import com.philkes.notallyx.utils.getExternalImagesDirectory
import com.philkes.notallyx.utils.getFileName
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.mimeTypeToFileExtension
import com.philkes.notallyx.utils.rename
import com.philkes.notallyx.utils.scheduleNoteReminders
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ImportExtensions"

/**
 * We only import the images/files referenced in notes. e.g If someone has added garbage to the ZIP
 * file, like a 100 MB image, ignore it.
 */
suspend fun ContextWrapper.importZip(
    zipFileUri: Uri,
    databaseFolder: File,
    zipPassword: String,
    importingBackup: MutableLiveData<ImportProgress>? = null,
) {
    importingBackup?.postValue(ImportProgress(indeterminate = true))
    try {
        val importedNotes =
            withContext(Dispatchers.IO) {
                val stream = requireNotNull(contentResolver.openInputStream(zipFileUri))
                val tempZipFile = File(databaseFolder, "TEMP.zip")
                stream.copyToFile(tempZipFile)
                val zipFile = ZipFile(tempZipFile)
                if (zipFile.isEncrypted) {
                    zipFile.setPassword(zipPassword.toCharArray())
                }
                zipFile.extractFile(
                    NotallyDatabase.DATABASE_NAME,
                    databaseFolder.path,
                    NotallyDatabase.DATABASE_NAME,
                )

                val database =
                    SQLiteDatabase.openDatabase(
                        File(databaseFolder, NotallyDatabase.DATABASE_NAME).path,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    )

                val labelCursor = database.query("Label", null, null, null, null, null, null)
                val baseNoteCursor = database.query("BaseNote", null, null, null, null, null, null)

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
                val imageRoot = getExternalImagesDirectory()
                val fileRoot = getExternalFilesDirectory()
                val audioRoot = getExternalAudioDirectory()
                baseNotes.forEach { baseNote ->
                    importFiles(
                        baseNote.images,
                        SUBFOLDER_IMAGES,
                        imageRoot,
                        zipFile,
                        current,
                        total,
                        importingBackup,
                    )
                    importFiles(
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
                            log(TAG, throwable = exception)
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

                val notallyDatabase =
                    NotallyDatabase.getDatabase(this@importZip, observePreferences = false).value
                notallyDatabase.getCommonDao().importBackup(baseNotes, labels)
                val reminders = notallyDatabase.getBaseNoteDao().getAllReminders()
                cancelNoteReminders(reminders)
                scheduleNoteReminders(reminders)
                baseNotes.size
            }
        databaseFolder.clearDirectory()
        val message = getQuantityString(R.plurals.imported_notes, importedNotes)
        showToast(message)
    } catch (e: ZipException) {
        if (e.type == ZipException.Type.WRONG_PASSWORD) {
            showToast(R.string.wrong_password)
        } else {
            log(TAG, throwable = e)
            showToast(R.string.invalid_backup)
        }
    } catch (e: Exception) {
        showToast(R.string.invalid_backup)
        log(TAG, throwable = e)
    } finally {
        importingBackup?.value = ImportProgress(inProgress = false)
    }
}

private fun ContextWrapper.importFiles(
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
        } catch (e: Exception) {
            log(TAG, throwable = e)
        } finally {
            importingBackup?.postValue(
                ImportProgress(current.getAndIncrement(), total, stage = ImportStage.IMPORT_FILES)
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
    val color =
        getString(getColumnIndexOrThrow("color"))?.parseToColorString() ?: BaseNote.COLOR_DEFAULT
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

    val remindersIndex = getColumnIndex("reminders")
    val reminders =
        if (remindersIndex != -1) {
            Converters.jsonToReminders(getString(remindersIndex))
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
        reminders,
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

fun Context.importPreferences(jsonFile: Uri, to: SharedPreferences.Editor): Boolean {
    try {
        val inputStream: InputStream? = contentResolver.openInputStream(jsonFile)
        val jsonString = inputStream?.bufferedReader()?.use { it.readText() } ?: return false
        val jsonObject = JSONObject(jsonString)
        to.clear()
        jsonObject.keys().forEach { key ->
            when (val value = jsonObject.get(key)) {
                is Int -> to.putInt(key, value)
                is Boolean -> to.putBoolean(key, value)
                is Double -> to.putFloat(key, value.toFloat())
                is Long -> to.putLong(key, value)
                is JSONArray -> {
                    val set = (0 until value.length()).map { value.getString(it) }.toSet()
                    to.putStringSet(key, set)
                }

                else -> to.putString(key, value.toString())
            }
        }
        return to.commit()
    } catch (e: Exception) {
        if (this is ContextWrapper) {
            log(TAG, "Import preferences from '$jsonFile' failed", throwable = e)
        } else {
            Log.e(TAG, "Import preferences from '$jsonFile' failed", e)
        }
        return false
    }
}

suspend fun Context.importFile(
    uri: Uri,
    directory: File,
    fileType: FileType,
    errorWhileRenaming: Int = R.string.error_while_renaming_file,
    proposedMimeType: String? = null,
): Pair<FileAttachment?, FileError?> {
    return withContext(Dispatchers.IO) {
        val document = requireNotNull(DocumentFile.fromSingleUri(this@importFile, uri))
        val displayName = document.name ?: getString(R.string.unknown_name)
        try {

            /*
            If we have reached this point, an SD card (emulated or real) exists and externalRoot
            is not null. externalRoot.exists() can be false if the folder `Images` has been deleted after
            the previous line, but externalRoot itself can't be null
            */
            val temp = File(directory, "Temp")

            val inputStream = requireNotNull(contentResolver.openInputStream(uri))
            inputStream.copyToFile(temp)

            val originalName = getFileName(uri)
            when (fileType) {
                FileType.IMAGE -> {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(temp.path, options)
                    val mimeType = options.outMimeType ?: proposedMimeType

                    if (mimeType != null) {
                        val extension = mimeType.mimeTypeToFileExtension()
                        if (extension != null) {
                            val name = "${UUID.randomUUID()}.$extension"
                            if (temp.rename(name)) {
                                return@withContext Pair(
                                    FileAttachment(name, originalName ?: name, mimeType),
                                    null,
                                )
                            } else {
                                // I don't expect this error to ever happen but just in
                                // case
                                return@withContext Pair(
                                    null,
                                    FileError(displayName, getString(errorWhileRenaming), fileType),
                                )
                            }
                        } else
                            return@withContext Pair(
                                null,
                                FileError(
                                    displayName,
                                    getString(R.string.image_format_not_supported),
                                    fileType,
                                ),
                            )
                    } else
                        return@withContext Pair(
                            null,
                            FileError(displayName, getString(R.string.invalid_image), fileType),
                        )
                }

                FileType.ANY -> {
                    val (mimeType, fileExtension) =
                        contentResolver.determineMimeTypeAndExtension(uri, proposedMimeType)
                    val name = "${UUID.randomUUID()}${fileExtension}"
                    if (temp.rename(name)) {
                        return@withContext Pair(
                            FileAttachment(name, originalName ?: name, mimeType),
                            null,
                        )
                    } else {
                        // I don't expect this error to ever happen but just in case
                        return@withContext Pair(
                            null,
                            FileError(displayName, getString(errorWhileRenaming), fileType),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (this is ContextWrapper) {
                log(TAG, throwable = e)
            } else {
                Log.e(TAG, "Import file failed", e)
            }
            return@withContext Pair(
                null,
                FileError(displayName, getString(R.string.unknown_error), fileType),
            )
        }
    }
}

suspend fun ContextWrapper.importFile(
    uri: Uri,
    proposedMimeType: String? = null,
): Pair<FileAttachment?, FileError?> {
    val filesRoot = getExternalFilesDirectory()
    requireNotNull(filesRoot) { "filesRoot is null" }
    return importFile(uri, filesRoot, FileType.ANY, proposedMimeType = proposedMimeType)
}

suspend fun ContextWrapper.importImage(
    uri: Uri,
    proposedMimeType: String? = null,
): Pair<FileAttachment?, FileError?> {
    val imagesRoot = getExternalImagesDirectory()
    requireNotNull(imagesRoot) { "imagesRoot is null" }
    return importFile(uri, imagesRoot, FileType.IMAGE, proposedMimeType = proposedMimeType)
}

suspend fun ContextWrapper.importAudio(original: File, deleteOriginalFile: Boolean): Audio {
    return withContext(Dispatchers.IO) {
        /*
        Regenerate because the directory may have been deleted between the time of activity creation
        and audio recording
        */
        val audioRoot = getExternalAudioDirectory()
        requireNotNull(audioRoot) { "audioRoot is null" }

        /*
        If we have reached this point, an SD card (emulated or real) exists and audioRoot
        is not null. audioRoot.exists() can be false if the folder `Audio` has been deleted after
        the previous line, but audioRoot itself can't be null
        */
        val name = "${UUID.randomUUID()}.m4a"
        val final = File(audioRoot, name)
        val input = FileInputStream(original)
        input.copyToFile(final)

        if (deleteOriginalFile) {
            original.delete()
        }

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(final.path)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        Audio(name, duration?.toLong(), System.currentTimeMillis())
    }
}
