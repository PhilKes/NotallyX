package com.omgodse.notally.changehistory

import com.omgodse.notally.recyclerview.ListManager

class ChangeCheckedForAllChange(
    internal val checked: Boolean,
    internal val changedPositions: Collection<Int>,
    internal val changedPositionsAfterSort: Collection<Int>,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.check(checked, changedPositions)
    }

    override fun undo() {
        listManager.check(!checked, changedPositionsAfterSort)
    }
}
