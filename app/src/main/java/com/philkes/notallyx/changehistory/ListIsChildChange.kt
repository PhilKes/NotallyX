package com.philkes.notallyx.changehistory

import com.philkes.notallyx.recyclerview.ListManager

class ListIsChildChange(isChild: Boolean, position: Int, private val listManager: ListManager) :
    ListPositionValueChange<Boolean>(isChild, !isChild, position) {

    override fun update(position: Int, value: Boolean, isUndo: Boolean) {
        listManager.changeIsChild(position, value, pushChange = false)
    }

    override fun toString(): String {
        return "IsChildChange position: $position isChild: $newValue"
    }
}
