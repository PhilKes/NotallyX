package com.philkes.notallyx.presentation.viewmodel.preference

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.philkes.notallyx.R

class NotesSortPreference(sharedPreferences: SharedPreferences) :
    BasePreference<NotesSort>(sharedPreferences, NotesSort(), R.string.notes_sorted_by) {

    override fun getValue(sharedPreferences: SharedPreferences): NotesSort {
        val sortedByName = sharedPreferences.getString(SORTED_BY_KEY, null)
        val sortedBy =
            sortedByName?.let {
                try {
                    NotesSortBy.fromValue(sortedByName)
                } catch (e: Exception) {
                    defaultValue.sortedBy
                }
            } ?: defaultValue.sortedBy
        val sortDirectionName = sharedPreferences.getString(SORT_DIRECTION_KEY, null)
        val sortDirection =
            sortDirectionName?.let {
                try {
                    SortDirection.valueOf(sortDirectionName)
                } catch (e: Exception) {
                    defaultValue.sortDirection
                }
            } ?: defaultValue.sortDirection
        return NotesSort(sortedBy, sortDirection)
    }

    override fun Editor.put(value: NotesSort) {
        putString(SORTED_BY_KEY, value.sortedBy.value)
        putString(SORT_DIRECTION_KEY, value.sortDirection.name)
    }

    companion object {
        const val SORTED_BY_KEY = "notesSorting"
        const val SORT_DIRECTION_KEY = "notesSortingDirection"
    }
}

enum class SortDirection(val textResId: Int, val iconResId: Int) {
    ASC(R.string.ascending, R.drawable.arrow_upward),
    DESC(R.string.descending, R.drawable.arrow_downward),
}

enum class NotesSortBy(val textResId: Int, val iconResId: Int, val value: String) {
    TITLE(R.string.title, R.drawable.sort_by_alpha, "autoSortByTitle"),
    CREATION_DATE(R.string.creation_date, R.drawable.calendar_add_on, "autoSortByCreationDate"),
    MODIFIED_DATE(R.string.modified_date, R.drawable.edit_calendar, "autoSortByModifiedDate"),
    COLOR(R.string.color, R.drawable.change_color, "autoSortByColor");

    companion object {
        fun fromValue(value: String): NotesSortBy? {
            return entries.find { it.value == value }
        }
    }
}

data class NotesSort(
    val sortedBy: NotesSortBy = NotesSortBy.CREATION_DATE,
    val sortDirection: SortDirection = SortDirection.DESC,
) : TextProvider {
    override fun getText(context: Context): String {
        return "${context.getString(sortedBy.textResId)} (${context.getString(sortDirection.textResId)})"
    }
}
