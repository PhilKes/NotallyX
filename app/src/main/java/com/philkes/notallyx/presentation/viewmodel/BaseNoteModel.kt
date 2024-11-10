package com.philkes.notallyx.presentation.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.print.PostPDFGenerator
import android.text.Html
import android.widget.Toast
import androidx.core.text.toHtml
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.dao.CommonDao
import com.philkes.notallyx.data.dao.LabelDao
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.data.imports.NotesImporter
import com.philkes.notallyx.data.model.Attachment
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.Content
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.SearchResult
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.viewmodel.preference.BasePreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.utils.ActionMode
import com.philkes.notallyx.utils.Cache
import com.philkes.notallyx.utils.IO.deleteAttachments
import com.philkes.notallyx.utils.IO.getBackupDir
import com.philkes.notallyx.utils.IO.getExportedPath
import com.philkes.notallyx.utils.IO.getExternalAudioDirectory
import com.philkes.notallyx.utils.IO.getExternalFilesDirectory
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.backup.Export.exportAsZip
import com.philkes.notallyx.utils.backup.Import.importZip
import com.philkes.notallyx.utils.backup.Migrations
import com.philkes.notallyx.utils.backup.XMLUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DateFormat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BaseNoteModel(private val app: Application) : AndroidViewModel(app) {

    private lateinit var database: NotallyDatabase
    private lateinit var labelDao: LabelDao
    private lateinit var commonDao: CommonDao
    private lateinit var baseNoteDao: BaseNoteDao

    private val labelCache = HashMap<String, Content>()

    var currentFile: File? = null

    lateinit var labels: LiveData<List<String>>
    var allNotes: LiveData<List<BaseNote>>? = null
    var allNotesObserver: Observer<List<BaseNote>>? = null
    var baseNotes: Content? = null
    var deletedNotes: Content? = null
    var archivedNotes: Content? = null

    val folder = NotNullLiveData(Folder.NOTES)

    var keyword = String()
        set(value) {
            if (field != value || searchResults?.value?.isEmpty() == true) {
                field = value
                searchResults!!.fetch(keyword, folder.value)
            }
        }

    var searchResults: SearchResult? = null

    private val pinned = Header(app.getString(R.string.pinned))
    private val others = Header(app.getString(R.string.others))

    val preferences = NotallyXPreferences.getInstance(app)

    val imageRoot = app.getExternalImagesDirectory()
    val fileRoot = app.getExternalFilesDirectory()
    private val audioRoot = app.getExternalAudioDirectory()

    val importProgress = MutableLiveData<ImportProgress>()
    val exportProgress = MutableLiveData<Progress>()
    val deletionProgress = MutableLiveData<Progress>()

    val actionMode = ActionMode()

    init {
        NotallyDatabase.getDatabase(app).observeForever(::init)
        folder.observeForever { newFolder -> searchResults!!.fetch(keyword, newFolder) }
    }

    private fun init(database: NotallyDatabase) {
        this.database = database
        baseNoteDao = database.getBaseNoteDao()
        labelDao = database.getLabelDao()
        commonDao = database.getCommonDao()

        labels = labelDao.getAll()

        allNotes?.removeObserver(allNotesObserver!!)
        allNotesObserver = Observer { list -> Cache.list = list }
        allNotes = baseNoteDao.getAllAsync()
        allNotes!!.observeForever(allNotesObserver!!)

        if (baseNotes == null) {
            baseNotes = Content(baseNoteDao.getFrom(Folder.NOTES), ::transform)
        } else {
            baseNotes!!.setObserver(baseNoteDao.getFrom(Folder.NOTES))
        }

        if (deletedNotes == null) {
            deletedNotes = Content(baseNoteDao.getFrom(Folder.DELETED), ::transform)
        } else {
            deletedNotes!!.setObserver(baseNoteDao.getFrom(Folder.DELETED))
        }

        if (archivedNotes == null) {
            archivedNotes = Content(baseNoteDao.getFrom(Folder.ARCHIVED), ::transform)
        } else {
            archivedNotes!!.setObserver(baseNoteDao.getFrom(Folder.ARCHIVED))
        }

        if (searchResults == null) {
            searchResults = SearchResult(viewModelScope, baseNoteDao, ::transform)
        } else {
            searchResults!!.baseNoteDao = baseNoteDao
        }

        viewModelScope.launch {
            val previousNotes = Migrations.getPreviousNotes(app)
            val previousLabels = Migrations.getPreviousLabels(app)
            if (previousNotes.isNotEmpty() || previousLabels.isNotEmpty()) {
                database.withTransaction {
                    labelDao.insert(previousLabels)
                    baseNoteDao.insert(previousNotes)
                    Migrations.clearAllLabels(app)
                    Migrations.clearAllFolders(app)
                }
            }
        }
    }

    fun getNotesByLabel(label: String): Content {
        if (labelCache[label] == null) {
            labelCache[label] = Content(baseNoteDao.getBaseNotesByLabel(label), ::transform)
        }
        return requireNotNull(labelCache[label])
    }

    private fun transform(list: List<BaseNote>) = transform(list, pinned, others)

    fun disableAutoBackup() {
        val value = preferences.autoBackup.value
        if (value.path != EMPTY_PATH) {
            clearPersistedUriPermissions(value.path)
        }
        savePreference(preferences.autoBackup, value.copy(path = EMPTY_PATH))
    }

    fun setAutoBackupPath(uri: Uri) {
        val value = preferences.autoBackup.value
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        app.contentResolver.takePersistableUriPermission(uri, flags)
        if (value.path != EMPTY_PATH) {
            clearPersistedUriPermissions(value.path)
        }
        savePreference(preferences.autoBackup, value.copy(path = uri.toString()))
    }

    fun enableExternalData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val database = NotallyDatabase.getDatabase(app, observePreferences = false).value
                database.checkpoint()
                NotallyDatabase.getCurrentDatabaseFile(app)
                    .copyTo(NotallyDatabase.getExternalDatabaseFile(app), overwrite = true)
            }
            savePreference(preferences.dataOnExternalStorage, true)
        }
    }

    fun disableExternalData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val database = NotallyDatabase.getDatabase(app, observePreferences = false).value
                database.checkpoint()
                NotallyDatabase.getCurrentDatabaseFile(app)
                    .copyTo(NotallyDatabase.getInternalDatabaseFile(app), overwrite = true)
                NotallyDatabase.getExternalDatabaseFiles(app).forEach {
                    if (it.exists()) {
                        it.delete()
                    }
                }
            }
            savePreference(preferences.dataOnExternalStorage, false)
        }
    }

    fun <T> savePreference(preference: BasePreference<T>, value: T) {
        executeAsync { preference.save(value) }
    }

    /**
     * Release previously persisted permissions, if any There is a hard limit of 128 before Android
     * 11, 512 after Check ->
     * https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
     */
    private fun clearPersistedUriPermissions(folderPath: String) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        app.contentResolver.persistedUriPermissions.forEach { permission ->
            val uriPath = permission.uri.path
            if (uriPath?.contains(folderPath) == true) {
                app.contentResolver.releasePersistableUriPermission(permission.uri, flags)
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { exportAsZip(uri, app, exportProgress) }
            Toast.makeText(app, R.string.saved_to_device, Toast.LENGTH_LONG).show()
        }
    }

    fun importZipBackup(uri: Uri, password: String) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Operations.log(app, throwable)
            Toast.makeText(app, R.string.invalid_backup, Toast.LENGTH_LONG).show()
        }

        val backupDir = app.getBackupDir()
        viewModelScope.launch(exceptionHandler) {
            importZip(app, uri, backupDir, password, importProgress)
        }
    }

    fun importXmlBackup(uri: Uri) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Operations.log(app, throwable)
            Toast.makeText(app, R.string.invalid_backup, Toast.LENGTH_LONG).show()
        }

        viewModelScope.launch(exceptionHandler) {
            val importedNotes =
                withContext(Dispatchers.IO) {
                    val stream = requireNotNull(app.contentResolver.openInputStream(uri))
                    val (baseNotes, labels) = XMLUtils.readBackupFromStream(stream)
                    commonDao.importBackup(baseNotes, labels)
                    baseNotes.size
                }
            val message = app.getQuantityString(R.plurals.imported_notes, importedNotes)
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    fun importFromOtherApp(uri: Uri, importSource: ImportSource) {
        val database = NotallyDatabase.getDatabase(app).value

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Toast.makeText(
                    app,
                    if (throwable is ImportException) {
                        throwable.textResId
                    } else R.string.invalid_backup,
                    Toast.LENGTH_LONG,
                )
                .show()
            Operations.log(app, throwable)
        }
        viewModelScope.launch(exceptionHandler) {
            val importedNotes =
                withContext(Dispatchers.IO) {
                    NotesImporter(app, database).import(uri, importSource, importProgress)
                }
            val message = app.getQuantityString(R.plurals.imported_notes, importedNotes)
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    fun writeCurrentFileToUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val output = app.contentResolver.openOutputStream(uri) as FileOutputStream
                output.channel.truncate(0)
                val input = FileInputStream(requireNotNull(currentFile))
                input.copyTo(output)
                input.close()
                output.close()
            }
            Toast.makeText(app, R.string.saved_to_device, Toast.LENGTH_LONG).show()
        }
    }

    suspend fun getJSONFile(baseNote: BaseNote) =
        withContext(Dispatchers.IO) {
            val file = File(app.getExportedPath(), "Untitled.json")
            val json = getJSON(baseNote)
            file.writeText(json)
            file
        }

    suspend fun getTXTFile(baseNote: BaseNote) =
        withContext(Dispatchers.IO) {
            val file = File(app.getExportedPath(), "Untitled.txt")
            val writer = file.bufferedWriter()

            val date = DateFormat.getDateInstance(DateFormat.FULL).format(baseNote.timestamp)

            val body =
                when (baseNote.type) {
                    Type.NOTE -> baseNote.body
                    Type.LIST -> Operations.getBody(baseNote.items)
                }

            if (baseNote.title.isNotEmpty()) {
                writer.append("${baseNote.title}\n\n")
            }
            if (preferences.showDateCreated()) {
                writer.append("$date\n\n")
            }
            writer.append(body)
            writer.close()

            file
        }

    suspend fun getHTMLFile(baseNote: BaseNote) =
        withContext(Dispatchers.IO) {
            val file = File(app.getExportedPath(), "Untitled.html")
            val html = getHTML(baseNote, preferences.showDateCreated())
            file.writeText(html)
            file
        }

    fun getPDFFile(baseNote: BaseNote, onResult: PostPDFGenerator.OnResult) {
        val file = File(app.getExportedPath(), "Untitled.pdf")
        val html = getHTML(baseNote, preferences.showDateCreated())
        PostPDFGenerator.create(file, html, app, onResult)
    }

    fun pinBaseNotes(pinned: Boolean) {
        val id = actionMode.selectedIds.toLongArray()
        actionMode.close(true)
        executeAsync { baseNoteDao.updatePinned(id, pinned) }
    }

    fun colorBaseNote(color: Color) {
        val ids = actionMode.selectedIds.toLongArray()
        actionMode.close(true)
        executeAsync { baseNoteDao.updateColor(ids, color) }
    }

    fun moveBaseNotes(folder: Folder): LongArray {
        val ids = actionMode.selectedIds.toLongArray()
        actionMode.close(false)
        moveBaseNotes(ids, folder)
        return ids
    }

    fun moveBaseNotes(ids: LongArray, folder: Folder) {
        executeAsync { baseNoteDao.move(ids, folder) }
    }

    fun updateBaseNoteLabels(labels: List<String>, id: Long) {
        actionMode.close(true)
        executeAsync { baseNoteDao.updateLabels(id, labels) }
    }

    fun deleteSelectedBaseNotes() {
        deleteBaseNotes(actionMode.selectedIds.toLongArray())
    }

    fun deleteAll() {
        viewModelScope.launch {
            deleteBaseNotes(withContext(Dispatchers.IO) { baseNoteDao.getAllIds().toLongArray() })
            withContext(Dispatchers.IO) { labelDao.deleteAll() }
            Toast.makeText(app, R.string.cleared_data, Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteBaseNotes(ids: LongArray) {
        val attachments = ArrayList<Attachment>()
        viewModelScope.launch {
            deletionProgress.value = Progress(indeterminate = true)
            val notes = withContext(Dispatchers.IO) { baseNoteDao.getByIds(ids) }
            notes.forEach { note ->
                attachments.addAll(note.images)
                attachments.addAll(note.files)
                attachments.addAll(note.audios)
            }
            actionMode.close(false)
            withContext(Dispatchers.IO) {
                baseNoteDao.delete(ids)
                app.deleteAttachments(attachments, ids, deletionProgress)
            }
        }
    }

    fun deleteAllTrashedBaseNotes() {
        viewModelScope.launch {
            val ids: LongArray
            val images = ArrayList<FileAttachment>()
            val files = ArrayList<FileAttachment>()
            val audios = ArrayList<Audio>()
            withContext(Dispatchers.IO) {
                ids = baseNoteDao.getDeletedNoteIds()
                val imageStrings = baseNoteDao.getDeletedNoteImages()
                val fileStrings = baseNoteDao.getDeletedNoteFiles()
                val audioStrings = baseNoteDao.getDeletedNoteAudios()
                imageStrings.flatMapTo(images) { json -> Converters.jsonToFiles(json) }
                fileStrings.flatMapTo(files) { json -> Converters.jsonToFiles(json) }
                audioStrings.flatMapTo(audios) { json -> Converters.jsonToAudios(json) }
                baseNoteDao.deleteFrom(Folder.DELETED)
            }
            val attachments = ArrayList<Attachment>(images.size + files.size + audios.size)
            attachments.addAll(images)
            attachments.addAll(files)
            attachments.addAll(audios)
            withContext(Dispatchers.IO) { app.deleteAttachments(attachments, ids) }
        }
    }

    suspend fun getAllLabels() = withContext(Dispatchers.IO) { labelDao.getArrayOfAll() }

    fun deleteLabel(value: String) {
        executeAsync { commonDao.deleteLabel(value) }
        val labelsHiddenPreference = preferences.labelsHiddenInNavigation
        val labelsHidden = labelsHiddenPreference.value.toMutableSet()
        if (labelsHidden.contains(value)) {
            labelsHidden.remove(value)
            savePreference(labelsHiddenPreference, labelsHidden)
        }
    }

    fun insertLabel(label: Label, onComplete: (success: Boolean) -> Unit) =
        executeAsyncWithCallback({ labelDao.insert(label) }, onComplete)

    fun updateLabel(oldValue: String, newValue: String, onComplete: (success: Boolean) -> Unit) {
        executeAsyncWithCallback({ commonDao.updateLabel(oldValue, newValue) }, onComplete)
        val labelsHiddenPreference = preferences.labelsHiddenInNavigation
        val labelsHidden = labelsHiddenPreference.value.toMutableSet()
        if (labelsHidden.contains(oldValue)) {
            labelsHidden.remove(oldValue)
            labelsHidden.add(newValue)
            savePreference(labelsHiddenPreference, labelsHidden)
        }
    }

    fun closeDatabase() {
        database.close()
    }

    private fun getJSON(baseNote: BaseNote): String {
        val jsonObject =
            JSONObject()
                .put("type", baseNote.type.name)
                .put("color", baseNote.color.name)
                .put("title", baseNote.title)
                .put("pinned", baseNote.pinned)
                .put("date-created", baseNote.timestamp)
                .put("labels", JSONArray(baseNote.labels))

        when (baseNote.type) {
            Type.NOTE -> {
                jsonObject.put("body", baseNote.body)
                jsonObject.put("spans", Converters.spansToJSONArray(baseNote.spans))
            }

            Type.LIST -> {
                jsonObject.put("items", Converters.itemsToJSONArray(baseNote.items))
            }
        }

        return jsonObject.toString(2)
    }

    private fun getHTML(baseNote: BaseNote, showDateCreated: Boolean) = buildString {
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(baseNote.timestamp)
        val title = Html.escapeHtml(baseNote.title)

        append("<!DOCTYPE html>")
        append("<html><head>")
        append("<meta charset=\"UTF-8\"><title>$title</title>")
        append("</head><body>")
        append("<h2>$title</h2>")

        if (showDateCreated) {
            append("<p>$date</p>")
        }

        when (baseNote.type) {
            Type.NOTE -> {
                val body = baseNote.body.applySpans(baseNote.spans).toHtml()
                append(body)
            }

            Type.LIST -> {
                append("<ol style=\"list-style: none; padding: 0;\">")
                baseNote.items.forEach { item ->
                    val body = Html.escapeHtml(item.body)
                    val checked = if (item.checked) "checked" else ""
                    append("<li><input type=\"checkbox\" $checked>$body</li>")
                }
                append("</ol>")
            }
        }
        append("</body></html>")
    }

    private fun executeAsync(function: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { function() }
    }

    companion object {

        fun transform(list: List<BaseNote>, pinned: Header, others: Header): List<Item> {
            if (list.isEmpty()) {
                return list
            } else {
                val firstNote = list[0]
                return if (firstNote.pinned) {
                    val newList = ArrayList<Item>(list.size + 2)
                    newList.add(pinned)

                    val firstUnpinnedNote = list.indexOfFirst { baseNote -> !baseNote.pinned }
                    list.forEachIndexed { index, baseNote ->
                        if (index == firstUnpinnedNote) {
                            newList.add(others)
                        }
                        newList.add(baseNote)
                    }
                    newList
                } else list
            }
        }
    }
}
