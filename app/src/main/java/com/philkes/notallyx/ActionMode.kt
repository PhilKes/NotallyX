package com.philkes.notallyx

import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.image.Event
import com.philkes.notallyx.model.BaseNote
import com.philkes.notallyx.preferences.BetterLiveData

class ActionMode {

    val enabled = BetterLiveData(false)
    val count = BetterLiveData(0)
    val selectedNotes = HashMap<Long, BaseNote>()
    val selectedIds = selectedNotes.keys
    val closeListener = MutableLiveData<Event<Set<Long>>>()

    private fun refresh() {
        count.value = selectedNotes.size
        enabled.value = selectedNotes.size != 0
    }

    fun add(id: Long, baseNote: BaseNote) {
        selectedNotes[id] = baseNote
        refresh()
    }

    fun remove(id: Long) {
        selectedNotes.remove(id)
        refresh()
    }

    fun close(notify: Boolean) {
        val previous = HashSet(selectedIds)
        selectedNotes.clear()
        refresh()
        if (notify && selectedNotes.size == 0) {
            closeListener.value = Event(previous)
        }
    }

    fun isEnabled() = enabled.value

    // We assume selectedNotes.size is 1
    fun getFirstNote() = selectedNotes.values.first()
}
