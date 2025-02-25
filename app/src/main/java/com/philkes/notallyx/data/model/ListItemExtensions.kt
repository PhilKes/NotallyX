package com.philkes.notallyx.data.model

import com.philkes.notallyx.presentation.view.note.listitem.areAllChecked

operator fun ListItem.plus(list: List<ListItem>): List<ListItem> {
    return mutableListOf(this) + list
}

fun ListItem.findChild(childId: Int): ListItem? {
    return this.children.find { child -> child.id == childId }
}

fun ListItem.check(checked: Boolean, checkChildren: Boolean = true) {
    this.checked = checked
    if (checkChildren) {
        this.children.forEach { child -> child.checked = checked }
    }
}

fun ListItem.shouldParentBeUnchecked(): Boolean {
    return children.isNotEmpty() && !children.areAllChecked() && checked
}

fun ListItem.shouldParentBeChecked(): Boolean {
    return children.isNotEmpty() && children.areAllChecked() && !checked
}
