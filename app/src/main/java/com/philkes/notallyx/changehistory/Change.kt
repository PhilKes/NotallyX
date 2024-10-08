package com.philkes.notallyx.changehistory

interface Change {
    fun redo()

    fun undo()
}
