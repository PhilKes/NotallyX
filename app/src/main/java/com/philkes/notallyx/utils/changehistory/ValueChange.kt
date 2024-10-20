package com.philkes.notallyx.utils.changehistory

abstract class ValueChange<T>(protected val newValue: T, protected val oldValue: T) : Change {

    override fun redo() {
        update(newValue, false)
    }

    override fun undo() {
        update(oldValue, true)
    }

    abstract fun update(value: T, isUndo: Boolean)
}
