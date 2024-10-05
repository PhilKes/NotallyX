package com.omgodse.notally.changehistory

abstract class ListIdValueChange<T>(
    internal val newValue: T,
    internal val oldValue: T,
    internal val itemId: Int,
) : Change {

    override fun redo() {
        update(itemId, newValue, false)
    }

    override fun undo() {
        update(itemId, oldValue, true)
    }

    abstract fun update(itemId: Int, value: T, isUndo: Boolean)
}
