package com.philkes.notallyx.presentation.view.main.sorting

import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.presentation.view.misc.SortDirection

class BaseNoteModifiedDateSort(adapter: RecyclerView.Adapter<*>?, sortDirection: SortDirection) :
    BaseNoteSort(adapter, sortDirection) {

    override fun compare(note1: BaseNote, note2: BaseNote, sortDirection: SortDirection): Int {
        val sort = note1.modifiedTimestamp.compareTo(note2.modifiedTimestamp)
        return if (sortDirection == SortDirection.ASC) sort else -1 * sort
    }
}
