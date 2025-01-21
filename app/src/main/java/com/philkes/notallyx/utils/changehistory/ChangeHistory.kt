package com.philkes.notallyx.utils.changehistory

import android.util.Log
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import kotlin.IllegalStateException

class ChangeHistory {
    private val changeStack = ArrayList<Change>()
    private var stackPointer = NotNullLiveData(-1)

    internal val canUndo = NotNullLiveData(false)
    internal val canRedo = NotNullLiveData(false)

    init {
        stackPointer.observeForever {
            canUndo.value = it > -1
            canRedo.value = it >= -1 && it < changeStack.size - 1
        }
    }

    fun push(change: Change) {
        popRedos()
        changeStack.add(change)
        stackPointer.value += 1
        Log.d(TAG, "addChange: $change")
    }

    fun redo() {
        stackPointer.value += 1
        if (stackPointer.value >= changeStack.size) {
            throw ChangeHistoryException("There is no Change to redo!")
        }
        val makeListAction = changeStack[stackPointer.value]
        Log.d(TAG, "redo: $makeListAction")
        makeListAction.redo()
    }

    fun undo() {
        if (stackPointer.value < 0) {
            throw ChangeHistoryException("There is no Change to undo!}")
        }
        val makeListAction = changeStack[stackPointer.value]
        Log.d(TAG, "undo: $makeListAction")
        makeListAction.undo()
        stackPointer.value -= 1
    }

    fun reset() {
        stackPointer.value = -1
        changeStack.clear()
    }

    internal fun lookUp(position: Int = 0): Change {
        if (stackPointer.value - position < 0) {
            throw ChangeHistoryException("ChangeHistory only has ${stackPointer.value+1} changes!")
        }
        return changeStack[stackPointer.value - position]
    }

    private fun popRedos() {
        while (changeStack.size > stackPointer.value + 1) {
            changeStack.removeAt(stackPointer.value + 1)
        }
    }

    inner class ChangeHistoryException(message: String) :
        IllegalStateException(
            "$message\nstackPointer: ${stackPointer.value}\nchangeStack:\n${changeStack.joinToString("\n")}"
        )

    companion object {
        private const val TAG = "ChangeHistory"
    }
}
