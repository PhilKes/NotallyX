package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.areAllChecked
import com.philkes.notallyx.data.model.deepCopy
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.findParentPosition
import com.philkes.notallyx.data.model.plus

fun List<ListItem>.shiftItemOrders(orderRange: IntRange, valueToAdd: Int) {
    this.forEach {
        if (it.order!! in orderRange) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun SortedList<ListItem>.shiftItemOrders(orderRange: IntRange, valueToAdd: Int) {
    this.forEach {
        if (it.order!! in orderRange) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun List<ListItem>.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach {
        if (it.order!! > threshold) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun List<ListItem>.shiftItemOrdersBetween(
    thresholdMin: Int,
    thresholdMax: Int,
    valueToAdd: Int,
    excludeParent: ListItem? = null,
) {
    this.forEach {
        if (
            it.order!! in (thresholdMin + 1 until thresholdMax) &&
                excludeParent?.let(it::isChildOf) != true
        ) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun SortedList<ListItem>.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach {
        if (it.order!! > threshold) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun SortedList<ListItem>.shiftItemOrdersBetween(
    thresholdMin: Int,
    thresholdMax: Int,
    valueToAdd: Int,
    excludeParent: ListItem? = null,
) {
    this.forEach {
        if (
            it.order!! in (thresholdMin + 1 until thresholdMax) &&
                excludeParent?.let(it::isChildOf) != true
        ) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun SortedList<ListItem>.toMutableList(): MutableList<ListItem> {
    return this.indices.map { this[it] }.toMutableList()
}

fun SortedItemsList.cloneList(): SortedList<ListItem> {
    val clone = SortedList(ListItem::class.java, callback)
    clone.addAll(toMutableList().cloneList())
    return clone
}

fun List<ListItem>.cloneList(): MutableList<ListItem> {
    val clone = this.indices.map { this[it].clone() as ListItem }.toMutableList()
    clone.forEach { itemClone ->
        itemClone.children =
            itemClone.children.map { child -> clone.first { it.id == child.id } }.toMutableList()
    }
    return clone
}

fun SortedList<ListItem>.findById(id: Int): Pair<Int, ListItem>? {
    val position = this.indexOfFirst { it.id == id } ?: return null
    return Pair(position, this[position])
}

fun SortedList<ListItem>.toReadableString(): String {
    return map { "$it order: ${it.order} id: ${it.id}" }.joinToString("\n")
}

fun SortedList<ListItem>.findParent(childPosition: Int): Pair<Int, ListItem>? {
    return findParent(this[childPosition])
}

fun SortedList<ListItem>.findParent(childItem: ListItem): Pair<Int, ListItem>? {
    this.indices.forEach {
        if (this[it].findChild(childItem.id) != null) {
            return Pair(it, this[it])
        }
    }
    return null
}

fun List<ListItem>.findParent(childItem: ListItem): Pair<Int, ListItem>? {
    this.indices.forEach {
        if (this[it].findChild(childItem.id) != null) {
            return Pair(it, this[it])
        }
    }
    return null
}

fun <R> SortedList<ListItem>.map(transform: (ListItem) -> R): List<R> {
    return (0 until this.size()).map { transform.invoke(this[it]) }
}

fun <R> SortedList<ListItem>.mapIndexed(transform: (Int, ListItem) -> R): List<R> {
    return (0 until this.size()).mapIndexed { idx, it -> transform.invoke(idx, this[it]) }
}

fun SortedList<ListItem>.forEach(function: (item: ListItem) -> Unit) {
    return (0 until this.size()).forEach { function.invoke(this[it]) }
}

fun SortedList<ListItem>.forEachIndexed(function: (idx: Int, item: ListItem) -> Unit) {
    for (i in 0 until this.size()) {
        function.invoke(i, this[i])
    }
}

fun SortedList<ListItem>.filter(function: (item: ListItem) -> Boolean): List<ListItem> {
    val list = mutableListOf<ListItem>()
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            list.add(this[i])
        }
    }
    return list.toList()
}

fun SortedList<ListItem>.find(function: (item: ListItem) -> Boolean): ListItem? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return this[i]
        }
    }
    return null
}

fun SortedList<ListItem>.indexOfFirst(function: (item: ListItem) -> Boolean): Int? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return i
        }
    }
    return null
}

val SortedList<ListItem>.lastIndex: Int
    get() = this.size() - 1

val SortedList<ListItem>.indices: IntRange
    get() = (0 until this.size())

fun SortedList<ListItem>.isNotEmpty(): Boolean {
    return size() > 0
}

fun SortedList<ListItem>.isEmpty(): Boolean {
    return size() == 0
}

fun MutableList<ListItem>.removeWithChildren(item: ListItem): Pair<Int, Int> {
    val index = indexOf(item)
    removeAll(item.children + item)
    return Pair(index, item.children.size + 1)
}

fun MutableList<ListItem>.addWithChildren(item: ListItem): Pair<Int, Int> {
    var insertIdx = indexOfFirst { it.order!! > item.order!! }
    if (insertIdx < 0) {
        insertIdx = size
    }
    addAll(insertIdx, item + item.children)
    return Pair(insertIdx, item.children.size + 1)
}

fun SortedList<ListItem>.addWithChildren(item: ListItem) {
    addAll(item + item.children)
}

fun SortedList<ListItem>.removeWithChildren(item: ListItem) {
    (item.children + item).forEach { remove(it) }
}

fun MutableList<ListItem>.updateChildInParent(
    position: Int,
    item: ListItem,
    clearChildren: Boolean = true,
) {
    val childIndex: Int?
    val parentInfo = findParent(item)
    val parent: ListItem?
    if (parentInfo == null) {
        val parentPosition = findParentPosition(position)!!
        childIndex = position - parentPosition - 1
        parent = this[parentPosition]
    } else {
        parent = parentInfo.second
        childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
        parent.children.removeAt(childIndex)
    }
    parent.children.add(childIndex, item)
    parent.children.addAll(childIndex + 1, item.children)
    if (clearChildren) {
        item.children.clear()
    }
}

fun List<ListItem>.splitByChecked(): Pair<List<ListItem>, List<ListItem>> = partition {
    it.checked && (!it.isChild || findParent(it)?.second?.children?.areAllChecked() == true)
}

fun Collection<ListItem>.init(resetIds: Boolean = true): List<ListItem> {
    val initializedItems = deepCopy()
    initList(initializedItems, resetIds)
    return initializedItems
}

private fun initList(list: List<ListItem>, resetIds: Boolean) {
    if (resetIds) {
        list.forEachIndexed { index, item -> item.id = index }
    }
    initOrders(list)
    initChildren(list)
}

private fun initChildren(list: List<ListItem>) {
    list.forEach { it.children.clear() }
    var parent: ListItem? = null
    list.forEach { item ->
        if (item.isChild && parent != null) {
            parent!!.children.add(item)
        } else {
            item.isChild = false
            parent = item
        }
    }
}

/** Makes sure every [ListItem.order] is valid and correct */
private fun initOrders(list: List<ListItem>): Boolean {
    var orders = list.map { it.order }.toMutableList()
    var invalidOrderFound = false
    list.forEachIndexed { idx, item ->
        if (item.order == null || orders.count { it == idx } > 1) {
            invalidOrderFound = true
            if (orders.contains(idx)) {
                shiftAllOrdersAfterItem(list, item)
            }
            item.order = idx
            orders = list.map { it.order }.toMutableList()
        }
    }
    return invalidOrderFound
}

private fun shiftAllOrdersAfterItem(list: List<ListItem>, item: ListItem) {
    // Move all orders after the item to ensure no duplicate orders
    val sortedByOrders = list.sortedBy { it.order }
    val position = sortedByOrders.indexOfFirst { it.id == item.id }
    for (i in position + 1..sortedByOrders.lastIndex) {
        sortedByOrders[i].order = sortedByOrders[i].order!! + 1
    }
}
