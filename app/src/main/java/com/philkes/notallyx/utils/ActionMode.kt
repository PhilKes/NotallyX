package com.philkes.notallyx.utils

import androidx.lifecycle.MutableLiveData
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData

class ActionMode {

    val enabled = NotNullLiveData(false)
    val loading = NotNullLiveData(false)
    val count = NotNullLiveData(0)
    val selectedNotes = HashMap<Long, BaseNote>()
    val selectedIds = selectedNotes.keys
    val closeListener = MutableLiveData<Event<Set<Long>>>()
    var addListener: (() -> Unit)? = null

    private fun refresh() {
        count.value = selectedNotes.size
        enabled.value = selectedNotes.size != 0
    }

    fun add(id: Long, baseNote: BaseNote) {
        selectedNotes[id] = baseNote
        refresh()
    }

    fun add(baseNotes: Collection<BaseNote>) {
        baseNotes.forEach { selectedNotes[it.id] = it }
        refresh()
        addListener?.invoke()
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

    fun updateSelected(availableItemIds: List<Long>?) {
        selectedNotes.keys
            .filter { availableItemIds?.contains(it) == false }
            .forEach { selectedNotes.remove(it) }
        refresh()
    }

    fun isEnabled() = enabled.value

    // We assume selectedNotes.size is 1
    fun getFirstNote() = selectedNotes.values.first()

    fun isEmpty() = selectedNotes.values.isEmpty()
}
