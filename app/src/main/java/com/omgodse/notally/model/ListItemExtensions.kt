package com.omgodse.notally.model

fun List<ListItem>.areAllChecked(except: ListItem? = null): Boolean {
    return this.none { !it.checked && it != except }
}

fun MutableList<ListItem>.containsId(id: Int): Boolean {
    return this.any { it.id == id }
}

operator fun ListItem.plus(list: List<ListItem>): List<ListItem> {
    return mutableListOf(this) + list
}

fun Collection<ListItem>.toReadableString(): String {
    return map { "$it uncheckedPos: ${it.order} id: ${it.id}" }.joinToString("\n")
}

fun ListItem.findChild(childId: Int): ListItem? {
    return this.children.find { child -> child.id == childId }
}
