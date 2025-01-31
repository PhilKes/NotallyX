package com.philkes.notallyx.data.dao

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.LabelsInBaseNote
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.Type

data class NoteIdReminder(val id: Long, val reminders: List<Reminder>)

data class NoteReminder(
    val id: Long,
    val title: String,
    val type: Type,
    val reminders: List<Reminder>,
)

@Dao
interface BaseNoteDao {

    @RawQuery fun query(query: SupportSQLiteQuery): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(baseNote: BaseNote): Long

    @Insert suspend fun insert(baseNotes: List<BaseNote>)

    @Update(entity = BaseNote::class) suspend fun update(labelsInBaseNotes: List<LabelsInBaseNote>)

    @Query("SELECT COUNT(*) FROM BaseNote") fun count(): Int

    @Query("DELETE FROM BaseNote WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM BaseNote WHERE id IN (:ids)") suspend fun delete(ids: LongArray)

    @Query("DELETE FROM BaseNote WHERE folder = :folder") suspend fun deleteFrom(folder: Folder)

    @Query("SELECT * FROM BaseNote WHERE folder = :folder ORDER BY pinned DESC, timestamp DESC")
    fun getFrom(folder: Folder): LiveData<List<BaseNote>>

    @Query("SELECT * FROM BaseNote WHERE folder = 'NOTES' ORDER BY pinned DESC, timestamp DESC")
    suspend fun getAllNotes(): List<BaseNote>

    @Query("SELECT * FROM BaseNote") fun getAllAsync(): LiveData<List<BaseNote>>

    @Query("SELECT * FROM BaseNote") fun getAll(): List<BaseNote>

    @Query("SELECT * FROM BaseNote WHERE id IN (:ids)") fun getByIds(ids: LongArray): List<BaseNote>

    @Query("SELECT B.id FROM BaseNote B") fun getAllIds(): List<Long>

    @Query("SELECT * FROM BaseNote WHERE id = :id") fun get(id: Long): BaseNote?

    @Query("SELECT images FROM BaseNote WHERE id = :id") fun getImages(id: Long): String

    @Query("SELECT images FROM BaseNote") fun getAllImages(): List<String>

    @Query("SELECT files FROM BaseNote") fun getAllFiles(): List<String>

    @Query("SELECT audios FROM BaseNote") fun getAllAudios(): List<String>

    @Query("SELECT id, reminders FROM BaseNote WHERE reminders IS NOT NULL AND reminders != '[]'")
    suspend fun getAllReminders(): List<NoteIdReminder>

    @Query("SELECT color FROM BaseNote WHERE id = :id ") fun getColorOfNote(id: Long): String

    @Query(
        "SELECT id, title, type, reminders FROM BaseNote WHERE reminders IS NOT NULL AND reminders != '[]'"
    )
    fun getAllRemindersAsync(): LiveData<List<NoteReminder>>

    @Query("SELECT id FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteIds(): LongArray

    @Query("SELECT images FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteImages(): List<String>

    @Query("SELECT files FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteFiles(): List<String>

    @Query("SELECT audios FROM BaseNote WHERE folder = 'DELETED'")
    suspend fun getDeletedNoteAudios(): List<String>

    @Query("UPDATE BaseNote SET folder = :folder WHERE id IN (:ids)")
    suspend fun move(ids: LongArray, folder: Folder)

    @Query("SELECT DISTINCT color FROM BaseNote") fun getAllColorsAsync(): LiveData<List<String>>

    @Query("SELECT DISTINCT color FROM BaseNote") suspend fun getAllColors(): List<String>

    @Query("UPDATE BaseNote SET color = :color WHERE id IN (:ids)")
    suspend fun updateColor(ids: LongArray, color: String)

    @Query("UPDATE BaseNote SET color = :newColor WHERE color = :oldColor")
    suspend fun updateColor(oldColor: String, newColor: String)

    @Query("UPDATE BaseNote SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun updatePinned(ids: LongArray, pinned: Boolean)

    @Query("UPDATE BaseNote SET labels = :labels WHERE id = :id")
    suspend fun updateLabels(id: Long, labels: List<String>)

    @Query("UPDATE BaseNote SET labels = :labels WHERE id IN (:ids)")
    suspend fun updateLabels(ids: LongArray, labels: List<String>)

    @Query("UPDATE BaseNote SET items = :items WHERE id = :id")
    suspend fun updateItems(id: Long, items: List<ListItem>)

    @Query("UPDATE BaseNote SET images = :images WHERE id = :id")
    suspend fun updateImages(id: Long, images: List<FileAttachment>)

    @Query("UPDATE BaseNote SET files = :files WHERE id = :id")
    suspend fun updateFiles(id: Long, files: List<FileAttachment>)

    @Query("UPDATE BaseNote SET audios = :audios WHERE id = :id")
    suspend fun updateAudios(id: Long, audios: List<Audio>)

    @Query("UPDATE BaseNote SET reminders = :reminders WHERE id = :id")
    suspend fun updateReminders(id: Long, reminders: List<Reminder>)

    /**
     * Both id and position can be invalid.
     *
     * Example of id being invalid - User adds a widget, then goes to Settings and clears app data.
     * Now the widget refers to a list which doesn't exist.
     *
     * Example of position being invalid - User adds a widget, goes to Settings, clears app data and
     * then imports a backup. Even if the backup contains the same list and it is inserted with the
     * same id, it may not be of the safe size.
     *
     * In this case, an exception will be thrown. It is the caller's responsibility to handle it.
     */
    suspend fun updateChecked(id: Long, position: Int, checked: Boolean) {
        val items = requireNotNull(get(id)).items
        items[position].checked = checked
        updateItems(id, items)
    }

    /** see [updateChecked] */
    suspend fun updateChecked(id: Long, positions: List<Int>, checked: Boolean) {
        val items = requireNotNull(get(id)).items
        positions.forEach { position -> items[position].checked = checked }
        updateItems(id, items)
    }

    /**
     * Since we store the labels as a JSON Array, it is not possible to perform operations on it.
     * Thus, we use the 'Like' query which can return false positives sometimes.
     *
     * For example, a request for all base notes having the label 'Important' will also return base
     * notes with the label 'Unimportant'. To prevent this, we use the extension function `map`
     * directly on the LiveData to filter the results accordingly.
     */
    fun getBaseNotesByLabel(label: String): LiveData<List<BaseNote>> {
        val result = getBaseNotesByLabel(label, Folder.NOTES)
        return result.map { list -> list.filter { baseNote -> baseNote.labels.contains(label) } }
    }

    @Query(
        "SELECT * FROM BaseNote WHERE folder = :folder AND labels LIKE '%' || :label || '%' ORDER BY pinned DESC, timestamp DESC"
    )
    fun getBaseNotesByLabel(label: String, folder: Folder): LiveData<List<BaseNote>>

    @Query(
        "SELECT * FROM BaseNote WHERE folder = :folder AND labels == '[]' ORDER BY pinned DESC, timestamp DESC"
    )
    fun getBaseNotesWithoutLabel(folder: Folder): LiveData<List<BaseNote>>

    suspend fun getListOfBaseNotesByLabel(label: String): List<BaseNote> {
        val result = getListOfBaseNotesByLabelImpl(label)
        return result.filter { baseNote -> baseNote.labels.contains(label) }
    }

    @Query("SELECT * FROM BaseNote WHERE labels LIKE '%' || :label || '%'")
    suspend fun getListOfBaseNotesByLabelImpl(label: String): List<BaseNote>

    fun getBaseNotesByKeyword(
        keyword: String,
        folder: Folder,
        label: String?,
    ): LiveData<List<BaseNote>> {
        val result =
            when (label) {
                null -> getBaseNotesByKeywordUnlabeledImpl(keyword, folder)
                "" -> getBaseNotesByKeywordImpl(keyword, folder)
                else -> getBaseNotesByKeywordImpl(keyword, folder, label)
            }
        return result.map { list -> list.filter { baseNote -> matchesKeyword(baseNote, keyword) } }
    }

    @Query(
        "SELECT * FROM BaseNote WHERE folder = :folder AND labels LIKE '%' || :label || '%' AND (title LIKE '%' || :keyword || '%' OR body LIKE '%' || :keyword || '%' OR items LIKE '%' || :keyword || '%') ORDER BY pinned DESC, timestamp DESC"
    )
    fun getBaseNotesByKeywordImpl(
        keyword: String,
        folder: Folder,
        label: String,
    ): LiveData<List<BaseNote>>

    @Query(
        "SELECT * FROM BaseNote WHERE folder = :folder AND (title LIKE '%' || :keyword || '%' OR body LIKE '%' || :keyword || '%' OR items LIKE '%' || :keyword || '%' OR labels LIKE '%' || :keyword || '%') ORDER BY pinned DESC, timestamp DESC"
    )
    fun getBaseNotesByKeywordImpl(keyword: String, folder: Folder): LiveData<List<BaseNote>>

    @Query(
        "SELECT * FROM BaseNote WHERE folder = :folder AND labels == '[]' AND (title LIKE '%' || :keyword || '%' OR body LIKE '%' || :keyword || '%' OR items LIKE '%' || :keyword || '%') ORDER BY pinned DESC, timestamp DESC"
    )
    fun getBaseNotesByKeywordUnlabeledImpl(
        keyword: String,
        folder: Folder,
    ): LiveData<List<BaseNote>>

    private fun matchesKeyword(baseNote: BaseNote, keyword: String): Boolean {
        if (baseNote.title.contains(keyword, true)) {
            return true
        }
        if (baseNote.body.contains(keyword, true)) {
            return true
        }
        for (label in baseNote.labels) {
            if (label.contains(keyword, true)) {
                return true
            }
        }
        for (item in baseNote.items) {
            if (item.body.contains(keyword, true)) {
                return true
            }
        }
        return false
    }
}
