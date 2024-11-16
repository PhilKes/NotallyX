package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.presentation.truncate

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

    override fun toString(): String {
        return "${javaClass.simpleName} at $position from: ${oldValue.toString().truncate(100)} to: ${newValue.toString().truncate(100)}"
    }
}
