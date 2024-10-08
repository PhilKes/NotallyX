package com.philkes.notallyx.utils.changehistory

interface Change {
    fun redo()

    fun undo()
}
