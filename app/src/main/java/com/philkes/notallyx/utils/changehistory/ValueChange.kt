package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.presentation.truncate

abstract class ValueChange<T>(protected val newValue: T, protected val oldValue: T) : Change {

    override fun redo() {
        update(newValue, false)
    }

    override fun undo() {
        update(oldValue, true)
    }

    abstract fun update(value: T, isUndo: Boolean)

    override fun toString(): String {
        return "${javaClass.simpleName} from: ${oldValue.toString().truncate(100)} to: ${newValue.toString().truncate(100)}"
    }
}
