package com.philkes.notallyx.presentation.view.note.listitem

import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.deepCopy
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.plus
import com.philkes.notallyx.data.model.shouldParentBeChecked
import com.philkes.notallyx.data.model.shouldParentBeUnchecked
import com.philkes.notallyx.utils.filter
import com.philkes.notallyx.utils.forEach
import com.philkes.notallyx.utils.indices
import com.philkes.notallyx.utils.map
import com.philkes.notallyx.utils.mapIndexed

fun List<ListItem>.shiftItemOrders(orderRange: IntRange, valueToAdd: Int) {
    this.forEach { it.shiftOrder(orderRange, valueToAdd) }
}

fun SortedList<ListItem>.shiftItemOrders(orderRange: IntRange, valueToAdd: Int) {
    forEach { it.shiftOrder(orderRange, valueToAdd) }
}

private fun ListItem.shiftOrder(orderRange: IntRange, valueToAdd: Int) {
    if (order!! in orderRange) {
        order = order!! + valueToAdd
    }
}

fun List<ListItem>.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach { it.shiftOrderHigher(threshold, valueToAdd) }
}

fun SortedList<ListItem>.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach { it.shiftOrderHigher(threshold, valueToAdd) }
}

private fun ListItem.shiftOrderHigher(threshold: Int, valueToAdd: Int) {
    if (order!! > threshold) {
        order = order!! + valueToAdd
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
    return indices.map { this[it] }.toMutableList()
}

fun List<ListItem>.cloneList(): MutableList<ListItem> {
    val clone = this.indices.map { this[it].clone() as ListItem }.toMutableList()
    clone.forEach { itemClone ->
        itemClone.children =
            itemClone.children.map { child -> clone.first { it.id == child.id } }.toMutableList()
    }
    return clone
}

fun SortedList<ListItem>.toReadableString(): String {
    return map { "$it order: ${it.order} id: ${it.id}" }.joinToString("\n")
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

fun List<ListItem>.findInsertIdx(item: ListItem): Int {
    return indexOfFirst { it.order!! > item.order!! }.let { if (it < 0) size else it }
}

fun List<ListItem>.firstBodyOrEmptyString() = firstOrNull()?.body ?: ""

fun MutableList<ListItem>.removeWithChildren(item: ListItem): Pair<Int, Int> {
    val index = indexOf(item)
    removeAll(item.children + item)
    return Pair(index, item.children.size + 1)
}

fun MutableList<ListItem>.addWithChildren(item: ListItem): Pair<Int, Int> {
    val insertIdx = findInsertIdx(item)
    addAll(insertIdx, item + item.children)
    return Pair(insertIdx, item.children.size + 1)
}

fun SortedList<ListItem>.addWithChildren(item: ListItem) {
    addAll(item + item.children)
}

fun SortedList<ListItem>.removeWithChildren(item: ListItem) {
    (item.children + item).forEach { remove(it) }
}

fun SortedList<ListItem>.setItems(list: MutableList<ListItem>) {
    clear()
    val (children, parents) = list.partition { it.isChild }
    //  Need to use replaceAll for auto-sorting checked items
    replaceAll(parents.toTypedArray(), false)
    addAll(children.toTypedArray(), false)
}

fun List<ListItem>.splitByChecked(): Pair<List<ListItem>, List<ListItem>> = partition {
    it.checked && (!it.isChild || findParent(it)?.second?.children?.areAllChecked() == true)
}

fun <R> List<R>.getOrNull(index: Int) = if (lastIndex >= index) this[index] else null

fun Collection<ListItem>.init(resetIds: Boolean = true): List<ListItem> {
    val initializedItems = deepCopy()
    initList(initializedItems, resetIds)
    checkBrokenList(initializedItems)
    return initializedItems
}

private fun checkBrokenList(list: List<ListItem>) {
    list.forEach { listItem ->
        if (listItem.shouldParentBeChecked()) {
            listItem.checked = true
        } else if (listItem.shouldParentBeUnchecked()) {
            listItem.checked = false
        }
    }
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

fun List<ListItem>.areAllChecked(except: ListItem? = null): Boolean {
    return this.none { !it.checked && (except == null || it.id != except.id) }
}

fun MutableList<ListItem>.containsId(id: Int): Boolean {
    return this.any { it.id == id }
}

fun Collection<ListItem>.toReadableString(): String {
    return map { "$it order: ${it.order} id: ${it.id}" }.joinToString("\n")
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

fun Collection<ListItem>.printList(text: String? = null) {
    text?.let { print("--------------\n$it\n") }
    println("--------------")
    println(toReadableString())
    println("--------------")
}

fun Collection<ListItem>.findParentsByChecked(checked: Boolean): List<ListItem> {
    return filter { !it.isChild && it.checked == checked }.distinct()
}

fun SortedList<ListItem>.findParentsByChecked(checked: Boolean): List<ListItem> {
    return filter { !it.isChild && it.checked == checked }.distinct()
}

fun SortedList<ListItem>.deleteCheckedItems() {
    mapIndexed { index, listItem -> Pair(index, listItem) }
        .filter { it.second.checked }
        .sortedBy { !it.second.isChild }
        .forEach { remove(it.second) }
}

fun MutableList<ListItem>.deleteCheckedItems(): Set<Int> {
    return mapIndexed { index, listItem -> Pair(index, listItem) }
        .filter { it.second.checked }
        .sortedBy { it.second.isChild }
        .onEach { remove(it.second) }
        .map { it.first }
        .toSet()
}

/**
 * Find correct parent for `childPosition` and update it's `children`.
 *
 * @return Correct parent
 */
fun List<ListItem>.refreshParent(childPosition: Int): ListItem? {
    val item = this[childPosition]
    findParent(item)?.let { (pos, parent) -> parent.children.removeWithChildren(item) }
    return findParentPosition(childPosition)?.let { parentPos ->
        val parent = this[parentPos]
        val children = parent.children
        val childIndex = children.findInsertIdx(item)
        children.addAll(childIndex, item + item.children)
        item.children.clear()
        parent
    }
}

fun SortedList<ListItem>.removeFromParent(child: ListItem): ListItem? {
    if (!child.isChild) {
        return null
    }
    return findParent(child)?.second?.also { it.children.remove(child) }
}

fun List<ListItem>.removeFromParent(child: ListItem): ListItem? {
    if (!child.isChild) {
        return null
    }
    return findParent(child)?.second?.also { it.children.remove(child) }
}

fun MutableList<ListItem>.addToParent(childPosition: Int) {
    findParentPosition(childPosition)?.let { parentPos ->
        this[parentPos].children.add(childPosition - parentPos - 1, this[childPosition])
    }
}

fun MutableList<ListItem>.removeChildrenBelowPositionFromParent(
    parentPosition: Int,
    thresholdPosition: Int,
): List<ListItem> {
    val children = this[parentPosition].children
    val childrenBelow =
        children.filterIndexed { idx, _ -> parentPosition + idx + 1 > thresholdPosition - 1 }
    children.removeAll(childrenBelow)
    return childrenBelow
}
