package com.philkes.notallyx.utils.backup

import android.content.Context
import android.content.SharedPreferences
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Label
import java.io.File

// Backwards compatibility from v3.2 to v3.3

fun Context.clearAllLabels() = getLabelsPreferences().edit().clear().commit()

fun Context.clearAllFolders() {
    getNotePath().listFiles()?.forEach { file -> file.delete() }
    getDeletedPath().listFiles()?.forEach { file -> file.delete() }
    getArchivedPath().listFiles()?.forEach { file -> file.delete() }
}

fun Context.getPreviousLabels(): List<Label> {
    val preferences = getLabelsPreferences()
    val labels =
        requireNotNull(
            preferences.getStringSet("labelItems", emptySet()),
            { "preference do not have 'labelItems' String set" },
        )
    return labels.map { value -> Label(value) }
}

fun Context.getPreviousNotes(): List<BaseNote> {
    val list = ArrayList<BaseNote>()
    getNotePath().listFiles()?.mapTo(list) { file -> file.toBaseNote(Folder.NOTES) }
    getDeletedPath().listFiles()?.mapTo(list) { file -> file.toBaseNote(Folder.DELETED) }
    getArchivedPath().listFiles()?.mapTo(list) { file -> file.toBaseNote(Folder.ARCHIVED) }
    return list
}

private fun Context.getNotePath() = getFolder("notes")

private fun Context.getDeletedPath() = getFolder("deleted")

private fun Context.getArchivedPath() = getFolder("archived")

private fun Context.getLabelsPreferences(): SharedPreferences {
    return getSharedPreferences("labelsPreferences", Context.MODE_PRIVATE)
}

private fun Context.getFolder(name: String): File {
    val folder = File(filesDir, name)
    if (!folder.exists()) {
        folder.mkdir()
    }
    return folder
}
