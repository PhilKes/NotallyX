package com.philkes.notallyx.data.dao

import androidx.room.Dao
import androidx.room.Transaction
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.data.model.LabelsInBaseNote

@Dao
abstract class CommonDao(private val database: NotallyDatabase) {

    @Transaction
    open suspend fun deleteLabel(value: String) {
        val labelsInBaseNotes =
            database.getBaseNoteDao().getListOfBaseNotesByLabel(value).map { baseNote ->
                val labels = ArrayList(baseNote.labels)
                labels.remove(value)
                LabelsInBaseNote(baseNote.id, labels)
            }
        database.getBaseNoteDao().update(labelsInBaseNotes)
        database.getLabelDao().delete(value)
    }

    @Transaction
    open suspend fun updateLabel(oldValue: String, newValue: String) {
        val labelsInBaseNotes =
            database.getBaseNoteDao().getListOfBaseNotesByLabel(oldValue).map { baseNote ->
                val labels = ArrayList(baseNote.labels)
                labels.remove(oldValue)
                labels.add(newValue)
                LabelsInBaseNote(baseNote.id, labels)
            }
        database.getBaseNoteDao().update(labelsInBaseNotes)
        database.getLabelDao().update(oldValue, newValue)
    }

    @Transaction
    open suspend fun importBackup(baseNotes: List<BaseNote>, labels: List<Label>) {
        database.getBaseNoteDao().insert(baseNotes)
        database.getLabelDao().insert(labels)
    }
}
