package com.philkes.notallyx.utils.changehistory

abstract class ListPositionValueChange<T>(
    internal val newValue: T,
    internal val oldValue: T,
    position: Int,
) : ListChange(position) {

    override fun redo() {
        update(position, newValue, false)
    }

    override fun undo() {
        update(position, oldValue, true)
    }

    abstract fun update(position: Int, value: T, isUndo: Boolean)
}
