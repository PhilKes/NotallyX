package com.philkes.notallyx.data.model

operator fun ListItem.plus(list: List<ListItem>): List<ListItem> {
    return mutableListOf(this) + list
}

fun ListItem.findChild(childId: Int): ListItem? {
    return this.children.find { child -> child.id == childId }
}

fun List<ListItem>.areAllChecked(except: ListItem? = null): Boolean {
    return this.none { !it.checked && it != except }
}

fun MutableList<ListItem>.containsId(id: Int): Boolean {
    return this.any { it.id == id }
}

fun Collection<ListItem>.toReadableString(): String {
    return map { "$it uncheckedPos: ${it.order} id: ${it.id}" }.joinToString("\n")
}

fun List<ListItem>.findChildrenPositions(parentPosition: Int): List<Int> {
    val childrenPositions = mutableListOf<Int>()
    for (position in parentPosition + 1 until this.size) {
        if (this[position].isChild) {
            childrenPositions.add(position)
        } else {
            break
        }
    }
    return childrenPositions
}

fun List<ListItem>.findParentPosition(childPosition: Int): Int? {
    for (position in childPosition - 1 downTo 0) {
        if (!this[position].isChild) {
            return position
        }
    }
    return null
}
