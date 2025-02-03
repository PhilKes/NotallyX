package com.philkes.notallyx.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.dao.CommonDao
import com.philkes.notallyx.data.dao.LabelDao
import com.philkes.notallyx.data.dao.NoteReminder
import com.philkes.notallyx.data.imports.ImportException
import com.philkes.notallyx.data.imports.ImportProgress
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.data.imports.NotesImporter
import com.philkes.notallyx.data.model.Attachment
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Content
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.SearchResult
import com.philkes.notallyx.data.model.toNoteIdReminders
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.viewmodel.preference.BasePreference
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_DEFAULT
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_UNLABELED
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.utils.ActionMode
import com.philkes.notallyx.utils.Cache
import com.philkes.notallyx.utils.MIME_TYPE_JSON
import com.philkes.notallyx.utils.backup.clearAllFolders
import com.philkes.notallyx.utils.backup.clearAllLabels
import com.philkes.notallyx.utils.backup.exportAsZip
import com.philkes.notallyx.utils.backup.exportPdfFile
import com.philkes.notallyx.utils.backup.exportPlainTextFile
import com.philkes.notallyx.utils.backup.getPreviousLabels
import com.philkes.notallyx.utils.backup.getPreviousNotes
import com.philkes.notallyx.utils.backup.importZip
import com.philkes.notallyx.utils.backup.readAsBackup
import com.philkes.notallyx.utils.cancelNoteReminders
import com.philkes.notallyx.utils.deleteAttachments
import com.philkes.notallyx.utils.getBackupDir
import com.philkes.notallyx.utils.getExternalImagesDirectory
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.scheduleNoteReminders
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.encryptDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BaseNoteModel(private val app: Application) : AndroidViewModel(app) {

    private lateinit var database: NotallyDatabase
    private lateinit var labelDao: LabelDao
    private lateinit var commonDao: CommonDao
    private lateinit var baseNoteDao: BaseNoteDao

    private val labelCache = HashMap<String, Content>()

    var selectedExportFile: DocumentFile? = null
    lateinit var selectedExportMimeType: ExportMimeType

    lateinit var labels: LiveData<List<String>>
    lateinit var reminders: LiveData<List<NoteReminder>>
    private var allNotes: LiveData<List<BaseNote>>? = null
    private var allNotesObserver: Observer<List<BaseNote>>? = null
    var baseNotes: Content? = null
    var deletedNotes: Content? = null
    var archivedNotes: Content? = null

    val folder = NotNullLiveData(Folder.NOTES)

    var currentLabel: String? = CURRENT_LABEL_EMPTY

    var keyword = String()
        set(value) {
            if (field != value || searchResults?.value?.isEmpty() == true) {
                field = value
                searchResults!!.fetch(keyword, folder.value, currentLabel)
            }
        }

    var searchResults: SearchResult? = null

    private val pinned = Header(app.getString(R.string.pinned))
    private val others = Header(app.getString(R.string.others))

    val preferences = NotallyXPreferences.getInstance(app)

    val imageRoot = app.getExternalImagesDirectory()

    val importProgress = MutableLiveData<ImportProgress>()
    val exportProgress = MutableLiveData<Progress>()
    val deletionProgress = MutableLiveData<Progress>()

    val actionMode = ActionMode()

    internal var showRefreshBackupsFolderAfterThemeChange = false

    init {
        NotallyDatabase.getDatabase(app).observeForever(::init)
        folder.observeForever { newFolder ->
            searchResults!!.fetch(keyword, newFolder, currentLabel)
        }
    }

    private fun init(database: NotallyDatabase) {
        this.database = database
        baseNoteDao = database.getBaseNoteDao()
        labelDao = database.getLabelDao()
        commonDao = database.getCommonDao()

        labels = labelDao.getAll()
        //        colors = baseNoteDao.getAllColorsAsync()
        reminders = baseNoteDao.getAllRemindersAsync()

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
            val previousNotes = app.getPreviousNotes()
            val previousLabels = app.getPreviousLabels()
            if (previousNotes.isNotEmpty() || previousLabels.isNotEmpty()) {
                database.withTransaction {
                    labelDao.insert(previousLabels)
                    baseNoteDao.insert(previousNotes)
                    app.clearAllLabels()
                    app.clearAllFolders()
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

    fun getNotesWithoutLabel(): Content {
        return Content(baseNoteDao.getBaseNotesWithoutLabel(Folder.NOTES), ::transform)
    }

    private fun transform(list: List<BaseNote>) = transform(list, pinned, others)

    fun disableBackups() {
        val value = preferences.backupsFolder.value
        if (value != EMPTY_PATH) {
            clearPersistedUriPermissions(value)
        }
        savePreference(preferences.backupsFolder, EMPTY_PATH)
        savePreference(
            preferences.periodicBackups,
            preferences.periodicBackups.value.copy(periodInDays = 0),
        )
    }

    fun setupBackupsFolder(uri: Uri) {
        val oldBackupsFolder = preferences.backupsFolder.value
        val newBackupsFolder = uri.toString()
        if (newBackupsFolder != oldBackupsFolder) {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            app.contentResolver.takePersistableUriPermission(uri, flags)
            if (oldBackupsFolder != EMPTY_PATH) {
                clearPersistedUriPermissions(oldBackupsFolder)
            }
            savePreference(preferences.backupsFolder, newBackupsFolder)
        }
        showRefreshBackupsFolderAfterThemeChange = false
    }

    fun enableDataInPublic(callback: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val database = NotallyDatabase.getDatabase(app, observePreferences = false).value
                database.checkpoint()
                val directory = NotallyDatabase.getExternalDatabaseFile(app).parentFile
                NotallyDatabase.getInternalDatabaseFiles(app).forEach {
                    it.copyTo(File(directory, it.name), overwrite = true)
                }
                //                database.close()
            }
            savePreference(preferences.dataInPublicFolder, true)
            callback?.invoke()
        }
    }

    fun disableDataInPublic(callback: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val database = NotallyDatabase.getDatabase(app, observePreferences = false).value
                database.checkpoint()
                val directory = NotallyDatabase.getInternalDatabaseFile(app).parentFile
                val oldFiles = NotallyDatabase.getExternalDatabaseFiles(app)
                oldFiles.forEach { it.copyTo(File(directory, it.name), overwrite = true) }
                //                database.close()
                oldFiles.forEach {
                    if (it.exists()) {
                        it.delete()
                    }
                }
            }
            savePreference(preferences.dataInPublicFolder, false)
            callback?.invoke()
        }
    }

    fun enableBiometricLock(cipher: Cipher) {
        savePreference(preferences.iv, cipher.iv)
        val passphrase = preferences.databaseEncryptionKey.init(cipher)
        encryptDatabase(app, passphrase)
        savePreference(preferences.fallbackDatabaseEncryptionKey, passphrase)
        savePreference(preferences.biometricLock, BiometricLock.ENABLED)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun disableBiometricLock(cipher: Cipher? = null, callback: (() -> Unit)? = null) {
        val encryptedPassphrase = preferences.databaseEncryptionKey.value
        val passphrase =
            cipher?.doFinal(encryptedPassphrase)
                ?: preferences.fallbackDatabaseEncryptionKey.value!!
        database.close()
        decryptDatabase(app, passphrase)
        savePreference(preferences.biometricLock, BiometricLock.DISABLED)
        callback?.invoke()
    }

    fun <T> savePreference(preference: BasePreference<T>, value: T) {
        viewModelScope.launch(Dispatchers.IO) { preference.save(value) }
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
            withContext(Dispatchers.IO) {
                app.exportAsZip(
                    uri,
                    password = preferences.backupPassword.value,
                    backupProgress = exportProgress,
                )
            }
            app.showToast(R.string.saved_to_device)
        }
    }

    fun importZipBackup(uri: Uri, password: String) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            app.log(TAG, throwable = throwable)
            app.showToast(R.string.invalid_backup)
        }

        val backupDir = app.getBackupDir()
        viewModelScope.launch(exceptionHandler) {
            app.importZip(uri, backupDir, password, importProgress)
        }
    }

    fun importXmlBackup(uri: Uri) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            app.log(TAG, throwable = throwable)
            app.showToast(R.string.invalid_backup)
        }

        viewModelScope.launch(exceptionHandler) {
            val importedNotes =
                withContext(Dispatchers.IO) {
                    val stream = requireNotNull(app.contentResolver.openInputStream(uri))
                    val (baseNotes, labels) = stream.readAsBackup()
                    commonDao.importBackup(baseNotes, labels)
                    baseNotes.size
                }
            val message = app.getQuantityString(R.plurals.imported_notes, importedNotes)
            app.showToast(message)
        }
    }

    fun importFromOtherApp(uri: Uri, importSource: ImportSource) {
        val database = NotallyDatabase.getDatabase(app, observePreferences = false).value

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Toast.makeText(
                    app,
                    if (throwable is ImportException) {
                        throwable.textResId
                    } else R.string.invalid_backup,
                    Toast.LENGTH_LONG,
                )
                .show()
            app.log(TAG, throwable = throwable)
        }
        viewModelScope.launch(exceptionHandler) {
            val importedNotes =
                withContext(Dispatchers.IO) {
                    NotesImporter(app, database).import(uri, importSource, importProgress)
                }
            val message = app.getQuantityString(R.plurals.imported_notes, importedNotes)
            app.showToast(message)
        }
    }

    fun exportSelectedFileToUri(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.use { output ->
                    app.contentResolver.openInputStream(selectedExportFile!!.uri)?.copyTo(output)
                }
            }
            app.showToast(R.string.saved_to_device)
        }
    }

    fun exportNotesToFolder(folderUri: Uri, notes: Collection<BaseNote>) {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            app.log(TAG, throwable = throwable)
            actionMode.close(true)
            exportProgress.postValue(Progress(inProgress = false))
            app.showToast(R.string.something_went_wrong)
        }
        viewModelScope.launch(exceptionHandler) {
            val counter = AtomicInteger(0)
            for (note in notes) {
                exportProgress.postValue(Progress(total = notes.size))
                when (selectedExportMimeType) {
                    ExportMimeType.TXT,
                    ExportMimeType.JSON,
                    ExportMimeType.HTML -> {
                        exportPlainTextFile(
                            app,
                            note,
                            selectedExportMimeType,
                            DocumentFile.fromTreeUri(app, folderUri)!!,
                            progress = exportProgress,
                            counter = counter,
                            total = notes.size,
                        )
                    }

                    ExportMimeType.PDF -> {
                        exportPdfFile(
                            app,
                            note,
                            DocumentFile.fromTreeUri(app, folderUri)!!,
                            progress = exportProgress,
                            counter = counter,
                            total = notes.size,
                        )
                    }
                }
            }
            actionMode.close(true)
            exportProgress.postValue(Progress(inProgress = false))
            app.showToast(R.string.saved_to_device)
        }
    }

    fun exportSelectedNotesToFolder(folderUri: Uri) {
        exportNotesToFolder(folderUri, actionMode.selectedNotes.values)
    }

    fun pinBaseNotes(pinned: Boolean) {
        val id = actionMode.selectedIds.toLongArray()
        actionMode.close(true)
        viewModelScope.launch(Dispatchers.IO) { baseNoteDao.updatePinned(id, pinned) }
    }

    fun colorBaseNote(color: String) {
        val ids = actionMode.selectedIds.toLongArray()
        actionMode.close(true)
        viewModelScope.launch(Dispatchers.IO) { baseNoteDao.updateColor(ids, color) }
    }

    fun changeColor(oldColor: String, newColor: String) {
        viewModelScope.launch(Dispatchers.IO) { baseNoteDao.updateColor(oldColor, newColor) }
    }

    fun moveBaseNotes(folder: Folder): LongArray {
        val ids = actionMode.selectedIds.toLongArray()
        actionMode.close(false)
        moveBaseNotes(ids, folder)
        return ids
    }

    fun moveBaseNotes(ids: LongArray, folder: Folder) {
        viewModelScope.launch(
            Dispatchers.IO
        ) { // Only reminders of notes in NOTES folder are active
            baseNoteDao.move(ids, folder)
            val notes = baseNoteDao.getByIds(ids).toNoteIdReminders()
            // Only reminders of notes in NOTES folder are active
            when (folder) {
                Folder.NOTES -> app.scheduleNoteReminders(notes)
                else -> app.cancelNoteReminders(notes)
            }
        }
    }

    fun updateBaseNoteLabels(labels: List<String>, id: Long) {
        actionMode.close(true)
        viewModelScope.launch(Dispatchers.IO) { baseNoteDao.updateLabels(id, labels) }
    }

    fun deleteSelectedBaseNotes() {
        deleteBaseNotes(actionMode.selectedIds.toLongArray())
    }

    fun deleteAll() {
        viewModelScope.launch {
            val (ids, noteReminders) =
                withContext(Dispatchers.IO) {
                    Pair(baseNoteDao.getAllIds().toLongArray(), baseNoteDao.getAllReminders())
                }
            app.cancelNoteReminders(noteReminders)
            deleteBaseNotes(ids)
            withContext(Dispatchers.IO) { labelDao.deleteAll() }
            savePreference(preferences.startView, START_VIEW_DEFAULT)
            app.showToast(R.string.cleared_data)
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
            app.cancelNoteReminders(notes.toNoteIdReminders())
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
        viewModelScope.launch(Dispatchers.IO) { commonDao.deleteLabel(value) }
        val labelsHiddenPreference = preferences.labelsHiddenInNavigation
        val labelsHidden = labelsHiddenPreference.value.toMutableSet()
        if (labelsHidden.contains(value)) {
            labelsHidden.remove(value)
            savePreference(labelsHiddenPreference, labelsHidden)
        }
        if (preferences.startView.value == value) {
            savePreference(preferences.startView, START_VIEW_DEFAULT)
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

    fun resetPreferences(callback: (restartRequired: Boolean) -> Unit) {
        val backupsFolder = preferences.backupsFolder.value
        val publicFolder = preferences.dataInPublicFolder.value
        val isThemeDefault = preferences.theme.value == Theme.FOLLOW_SYSTEM
        val finishCallback = { callback(!isThemeDefault) }
        if (preferences.biometricLock.value == BiometricLock.ENABLED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                disableBiometricLock {
                    finishResetPreferencesAfterBiometric(
                        publicFolder,
                        backupsFolder,
                        finishCallback,
                    )
                }
            } else finishResetPreferencesAfterBiometric(publicFolder, backupsFolder, finishCallback)
        } else finishResetPreferencesAfterBiometric(publicFolder, backupsFolder, finishCallback)
    }

    private fun finishResetPreferencesAfterBiometric(
        publicFolder: Boolean,
        backupsFolder: String,
        callback: (() -> Unit),
    ) {
        if (publicFolder) {
            refreshDataInPublicFolder(false) { finishResetPreferences(backupsFolder, callback) }
        } else finishResetPreferences(backupsFolder, callback)
    }

    private fun finishResetPreferences(backupsFolder: String, callback: () -> Unit) {
        preferences.reset()
        if (backupsFolder != EMPTY_PATH) {
            clearPersistedUriPermissions(backupsFolder)
        }
        callback()
    }

    fun importPreferences(
        context: Context,
        uri: Uri,
        askForUriPermissions: (uri: Uri) -> Unit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val oldBackupsFolder = preferences.backupsFolder.value
        val dataInPublicFolderBefore = preferences.dataInPublicFolder.value
        val themeBefore = preferences.theme.value
        val oldStartView = preferences.startView.value

        val success = preferences.import(context, uri)

        val dataInPublicFolder = preferences.dataInPublicFolder.getFreshValue()
        if (dataInPublicFolderBefore != dataInPublicFolder) {
            refreshDataInPublicFolder(dataInPublicFolder) {
                preferences.dataInPublicFolder.refresh()
                finishImportPreferences(
                    oldBackupsFolder,
                    themeBefore,
                    oldStartView,
                    context,
                    askForUriPermissions,
                ) {
                    if (success) {
                        onSuccess()
                    } else onFailure()
                }
            }
        } else
            finishImportPreferences(
                oldBackupsFolder,
                themeBefore,
                oldStartView,
                context,
                askForUriPermissions,
            ) {
                if (success) {
                    onSuccess()
                } else onFailure()
            }
    }

    private fun finishImportPreferences(
        oldBackupsFolder: String,
        themeBefore: Theme,
        oldStartView: String,
        context: Context,
        askForUriPermissions: (uri: Uri) -> Unit,
        callback: () -> Unit,
    ) {
        val backupFolder = preferences.backupsFolder.getFreshValue()
        if (oldBackupsFolder != backupFolder) {
            showRefreshBackupsFolderAfterThemeChange = true
            if (themeBefore == preferences.theme.getFreshValue()) {
                refreshBackupsFolder(context, backupFolder, askForUriPermissions)
            }
        } else {
            showRefreshBackupsFolderAfterThemeChange = false
        }
        val startView = preferences.startView.getFreshValue()
        if (oldStartView != startView) {
            refreshStartView(startView, oldStartView)
        }
        preferences.theme.refresh()
        callback()
    }

    fun refreshBackupsFolder(
        context: Context,
        backupFolder: String = preferences.backupsFolder.value,
        askForUriPermissions: (uri: Uri) -> Unit,
    ) {
        try {
            val backupFolderUri = backupFolder.toUri()
            MaterialAlertDialogBuilder(context)
                .setMessage(R.string.auto_backups_folder_rechoose)
                .setCancelButton { _, _ -> showRefreshBackupsFolderAfterThemeChange = false }
                .setOnDismissListener { showRefreshBackupsFolderAfterThemeChange = false }
                .setPositiveButton(R.string.choose_folder) { _, _ ->
                    askForUriPermissions(backupFolderUri)
                }
                .show()
        } catch (e: Exception) {
            showRefreshBackupsFolderAfterThemeChange = false
            disableBackups()
        }
    }

    private fun refreshDataInPublicFolder(dataInPublicFolder: Boolean, callback: () -> Unit) {
        if (dataInPublicFolder) {
            enableDataInPublic(callback)
        } else {
            disableDataInPublic(callback)
        }
    }

    private fun refreshStartView(startView: String, oldStartView: String) {
        if (startView in setOf(START_VIEW_DEFAULT, START_VIEW_UNLABELED)) {
            savePreference(preferences.startView, startView)
        } else {
            viewModelScope.launch {
                val startViewLabelExists =
                    withContext(Dispatchers.IO) { labelDao.exists(startView) }
                savePreference(
                    preferences.startView,
                    if (startViewLabelExists) startView else oldStartView,
                )
            }
        }
    }

    companion object {
        private const val TAG = "BaseNoteModel"

        const val CURRENT_LABEL_EMPTY = ""
        val CURRENT_LABEL_NONE: String? = null

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

enum class ExportMimeType(val mimeType: String, val fileExtension: String) {
    TXT("text/plain", "txt"),
    PDF("application/pdf", "pdf"),
    JSON(MIME_TYPE_JSON, "json"),
    HTML("text/html", "html"),
}
