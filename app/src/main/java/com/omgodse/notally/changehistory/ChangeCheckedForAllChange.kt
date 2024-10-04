package com.omgodse.notally.changehistory

import com.omgodse.notally.recyclerview.ListManager

class ChangeCheckedForAllChange(
    internal val checked: Boolean,
    internal val changedIds: Collection<Int>,
    private val listManager: ListManager,
) : Change {
    override fun redo() {
        listManager.checkByIds(checked, changedIds)
    }

    override fun undo() {
        listManager.checkByIds(!checked, changedIds)
    }

    override fun toString(): String {
        return "ChangeCheckedForAllChange checked: $checked changedIds: ${changedIds.joinToString(",")}"
    }
}
