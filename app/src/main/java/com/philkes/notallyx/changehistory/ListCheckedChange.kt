package com.philkes.notallyx.changehistory

import com.philkes.notallyx.recyclerview.ListManager

class ListCheckedChange(checked: Boolean, itemId: Int, private val listManager: ListManager) :
    ListIdValueChange<Boolean>(checked, !checked, itemId) {

    override fun update(itemId: Int, value: Boolean, isUndo: Boolean) {
        listManager.changeCheckedById(itemId, value, pushChange = false)
    }

    override fun toString(): String {
        return "CheckedChange id: $itemId isChecked: $newValue"
    }
}
