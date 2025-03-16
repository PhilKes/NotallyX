package com.philkes.notallyx.presentation.viewmodel

import android.app.Application
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.core.text.getSpans
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.dao.BaseNoteDao
import com.philkes.notallyx.data.dao.NoteIdReminder
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.SpanRepresentation
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.attachmentsDifferFrom
import com.philkes.notallyx.data.model.copy
import com.philkes.notallyx.data.model.deepCopy
import com.philkes.notallyx.presentation.activity.note.reminders.RemindersActivity.Companion.NEW_REMINDER_ID
import com.philkes.notallyx.presentation.applySpans
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.Progress
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.Cache
import com.philkes.notallyx.utils.Event
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.backup.checkBackupOnSave
import com.philkes.notallyx.utils.backup.importAudio
import com.philkes.notallyx.utils.backup.importFile
import com.philkes.notallyx.utils.cancelNoteReminders
import com.philkes.notallyx.utils.cancelReminder
import com.philkes.notallyx.utils.deleteAttachments
import com.philkes.notallyx.utils.getExternalAudioDirectory
import com.philkes.notallyx.utils.getExternalFilesDirectory
import com.philkes.notallyx.utils.getExternalImagesDirectory
import com.philkes.notallyx.utils.getTempAudioFile
import com.philkes.notallyx.utils.scheduleReminder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias BackupFile = Pair<String?, File>

class NotallyModel(private val app: Application) : AndroidViewModel(app) {

    private val database = NotallyDatabase.getDatabase(app)
    private lateinit var baseNoteDao: BaseNoteDao

    val preferences = NotallyXPreferences.getInstance(app)
    val textSize = preferences.textSize.value

    var isNewNote = true

    var type = Type.NOTE

    var id = 0L
    var folder = Folder.NOTES
    var color = BaseNote.COLOR_DEFAULT

    var title = String()
    var pinned = false
    var timestamp = System.currentTimeMillis()
    var modifiedTimestamp = System.currentTimeMillis()

    val labels = ArrayList<String>()

    var body: Editable = SpannableStringBuilder()

    val items = ArrayList<ListItem>()
    val images = NotNullLiveData<List<FileAttachment>>(emptyList())
    val files = NotNullLiveData<List<FileAttachment>>(emptyList())
    val audios = NotNullLiveData<List<Audio>>(emptyList())
    val reminders = NotNullLiveData<List<Reminder>>(emptyList())

    val addingFiles = MutableLiveData<Progress>()
    val eventBus = MutableLiveData<Event<List<FileError>>>()

    var imageRoot = app.getExternalImagesDirectory()
    var audioRoot = app.getExternalAudioDirectory()
    var filesRoot = app.getExternalFilesDirectory()

    var originalNote: BaseNote? = null

    init {
        database.observeForever { baseNoteDao = it.getBaseNoteDao() }
    }

    fun addAudio() {
        viewModelScope.launch {
            val audio = app.importAudio(app.getTempAudioFile(), true)
            val copy = ArrayList(audios.value)
            copy.add(audio)
            audios.value = copy
            updateAudios()
        }
    }

    fun deleteAudio(audio: Audio) {
        viewModelScope.launch {
            val copy = ArrayList(audios.value)
            copy.remove(audio)
            audios.value = copy
            updateAudios()
            withContext(Dispatchers.IO) { app.deleteAttachments(arrayListOf(audio)) }
        }
    }

    fun addImages(uris: Array<Uri>) {
        /*
        Regenerate because the directory may have been deleted between the time of activity creation
        and image addition
         */
        imageRoot = app.getExternalImagesDirectory()
        requireNotNull(imageRoot) { "imageRoot is null" }
        addFiles(uris, imageRoot!!, FileType.IMAGE)
    }

    fun addFiles(uris: Array<Uri>) {
        /*
        Regenerate because the directory may have been deleted between the time of activity creation
        and image addition
         */
        filesRoot = app.getExternalFilesDirectory()
        requireNotNull(filesRoot) { "filesRoot is null" }
        addFiles(uris, filesRoot!!, FileType.ANY)
    }

    private fun addFiles(uris: Array<Uri>, directory: File, fileType: FileType) {
        val errorWhileRenaming =
            if (fileType == FileType.IMAGE) {
                R.string.error_while_renaming_image
            } else {
                R.string.error_while_renaming_file
            }
        viewModelScope.launch {
            addingFiles.postValue(Progress(0, uris.size))

            val successes = ArrayList<FileAttachment>()
            val errors = ArrayList<FileError>()

            uris.forEachIndexed { index, uri ->
                val (fileAttachment, error) =
                    app.importFile(uri, directory, fileType, errorWhileRenaming)
                fileAttachment?.let { successes.add(it) }
                error?.let { errors.add(it) }
                addingFiles.postValue(Progress(index + 1, uris.size))
            }

            addingFiles.postValue(Progress(inProgress = false))

            if (successes.isNotEmpty()) {
                val copy =
                    when (fileType) {
                        FileType.IMAGE -> ArrayList(images.value)
                        FileType.ANY -> ArrayList(files.value)
                    }
                copy.addAll(successes)
                when (fileType) {
                    FileType.IMAGE -> {
                        images.value = copy
                        updateImages()
                    }

                    FileType.ANY -> {
                        files.value = copy
                        updateFiles()
                    }
                }
            }

            if (errors.isNotEmpty()) {
                eventBus.value = Event(errors)
            }
        }
    }

    fun deleteImages(list: ArrayList<FileAttachment>) {
        viewModelScope.launch {
            val copy = ArrayList(images.value)
            copy.removeAll(list)
            images.value = copy
            updateImages()
            withContext(Dispatchers.IO) { app.deleteAttachments(list) }
        }
    }

    fun deleteFiles(list: ArrayList<FileAttachment>) {
        viewModelScope.launch {
            val copy = ArrayList(files.value)
            copy.removeAll(list)
            files.value = copy
            updateFiles()
            withContext(Dispatchers.IO) { app.deleteAttachments(list) }
        }
    }

    fun setLabels(list: List<String>) {
        labels.clear()
        labels.addAll(list)
    }

    suspend fun setState(id: Long) {
        if (id != 0L) {
            isNewNote = false

            val cachedNote = Cache.list.find { baseNote -> baseNote.id == id }
            val baseNote = cachedNote ?: withContext(Dispatchers.IO) { baseNoteDao.get(id) }

            if (baseNote != null) {
                originalNote = baseNote.deepCopy()

                this.id = id
                folder = baseNote.folder
                color = baseNote.color

                title = baseNote.title
                pinned = baseNote.pinned
                timestamp = baseNote.timestamp
                modifiedTimestamp = baseNote.modifiedTimestamp

                setLabels(baseNote.labels)

                body = baseNote.body.applySpans(baseNote.spans)

                items.clear()
                items.addAll(baseNote.items)

                images.value = baseNote.images
                files.value = baseNote.files
                audios.value = baseNote.audios
                reminders.value = baseNote.reminders
            } else {
                originalNote = createBaseNote()
                app.showToast(R.string.cant_find_note)
            }
        } else originalNote = createBaseNote()
    }

    private suspend fun createBaseNote(): BaseNote {
        val baseNote = getBaseNote()
        id = withContext(Dispatchers.IO) { baseNoteDao.insert(baseNote) }
        return baseNote.copy(id = id)
    }

    suspend fun deleteBaseNote(checkAutoSave: Boolean = true) {
        app.cancelNoteReminders(listOf(NoteIdReminder(id, reminders.value)))
        withContext(Dispatchers.IO) { baseNoteDao.delete(id) }
        WidgetProvider.sendBroadcast(app, longArrayOf(id))
        val attachments = ArrayList(images.value + files.value + audios.value)
        if (attachments.isNotEmpty()) {
            withContext(Dispatchers.IO) { app.deleteAttachments(attachments) }
        }
        if (checkAutoSave) {
            app.checkBackupOnSave(preferences, forceFullBackup = true)
        }
    }

    fun setItems(items: List<ListItem>) {
        this.items.clear()
        this.items.addAll(items)
    }

    suspend fun saveNote(checkBackupOnSave: Boolean = true): Long {
        return withContext(Dispatchers.IO) {
            val note = getBaseNote()
            val id = baseNoteDao.insert(note)
            if (checkBackupOnSave) {
                checkBackupOnSave(note)
            }
            originalNote = note.deepCopy()
            return@withContext id
        }
    }

    suspend fun checkBackupOnSave(note: BaseNote = getBaseNote()) {
        app.checkBackupOnSave(
            preferences,
            note = note,
            forceFullBackup = originalNote?.attachmentsDifferFrom(note) == true,
        )
    }

    fun isEmpty(): Boolean {
        return title.isEmpty() &&
            body.isEmpty() &&
            items.none { item -> item.body.isNotEmpty() } &&
            files.value.isEmpty() &&
            images.value.isEmpty() &&
            audios.value.isEmpty()
    }

    fun isModified(): Boolean {
        return getBaseNote() != originalNote
    }

    private suspend fun updateImages() {
        withContext(Dispatchers.IO) { baseNoteDao.updateImages(id, images.value) }
    }

    private suspend fun updateFiles() {
        withContext(Dispatchers.IO) { baseNoteDao.updateFiles(id, files.value) }
    }

    private suspend fun updateAudios() {
        withContext(Dispatchers.IO) { baseNoteDao.updateAudios(id, audios.value) }
    }

    fun getBaseNote(): BaseNote {
        val spans = getFilteredSpans(body)
        val body = this.body.toString()
        val nonEmptyItems = this.items.filter { item -> item.body.isNotEmpty() }
        return BaseNote(
            id,
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
            nonEmptyItems,
            images.value,
            files.value,
            audios.value,
            reminders.value,
        )
    }

    private fun getFilteredSpans(spanned: Spanned): ArrayList<SpanRepresentation> {
        val representations = LinkedHashSet<SpanRepresentation>()
        spanned.getSpans<CharacterStyle>().forEach { span ->
            val end = spanned.getSpanEnd(span)
            val start = spanned.getSpanStart(span)
            val representation =
                SpanRepresentation(start, end, false, false, null, false, false, false)

            when (span) {
                is StyleSpan -> {
                    representation.bold = span.style == Typeface.BOLD
                    representation.italic = span.style == Typeface.ITALIC
                }

                is URLSpan -> {
                    representation.link = true
                    representation.linkData = span.url
                }
                is TypefaceSpan -> representation.monospace = span.family == "monospace"
                is StrikethroughSpan -> representation.strikethrough = true
            }

            if (representation.isNotUseless()) {
                representations.add(representation)
            }
        }
        return getFilteredRepresentations(ArrayList(representations))
    }

    private fun getFilteredRepresentations(
        representations: ArrayList<SpanRepresentation>
    ): ArrayList<SpanRepresentation> {
        representations.forEachIndexed { index, representation ->
            val match =
                representations.find { spanRepresentation ->
                    spanRepresentation.isEqualInSize(representation)
                }
            if (match != null && representations.indexOf(match) != index) {
                if (match.bold) {
                    representation.bold = true
                }
                if (match.link) {
                    representation.link = true
                    representation.linkData = match.linkData
                }
                if (match.italic) {
                    representation.italic = true
                }
                if (match.monospace) {
                    representation.monospace = true
                }
                if (match.strikethrough) {
                    representation.strikethrough = true
                }
                val copy = ArrayList(representations)
                copy[index] = representation
                copy.remove(match)
                return getFilteredRepresentations(copy)
            }
        }
        return representations
    }

    suspend fun removeReminder(reminder: Reminder) {
        app.cancelReminder(this.id, reminder.id)
        val updatedReminders = reminders.value.filter { it.id != reminder.id }
        updateReminders(updatedReminders)
    }

    suspend fun addReminder(reminder: Reminder) {
        val updatedReminders = ArrayList(reminders.value)
        val newReminder = reminder.copy(id = (updatedReminders.maxOfOrNull { it.id } ?: -1) + 1)
        updatedReminders.add(newReminder)
        updateReminders(updatedReminders)
        app.scheduleReminder(id, newReminder)
    }

    suspend fun updateReminder(updatedReminder: Reminder) {
        if (updatedReminder.id != NEW_REMINDER_ID) {
            app.cancelReminder(id, updatedReminder.id)
        }
        val updatedReminders = reminders.value.copy().toMutableList()
        val idx = updatedReminders.indexOfFirst { it.id == updatedReminder.id }
        if (idx != -1) {
            updatedReminders[idx] = updatedReminder
            updateReminders(updatedReminders)
            app.scheduleReminder(id, updatedReminder)
        }
    }

    private suspend fun updateReminders(updatedReminders: List<Reminder>) {
        reminders.value = updatedReminders
        withContext(Dispatchers.IO) { baseNoteDao.updateReminders(id, updatedReminders) }
    }

    enum class FileType {
        IMAGE,
        ANY,
    }
}
