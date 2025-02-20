package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.findParentPosition
import com.philkes.notallyx.data.model.plus

fun ListItemSortedList.deleteItem(item: ListItem) {
    val itemsBySortPosition = this.toMutableList().sortedBy { it.order }
    val positionOfDeletedItem = itemsBySortPosition.indexOfFirst { it.id == item.id }
    for (i in positionOfDeletedItem + 1..itemsBySortPosition.lastIndex) {
        itemsBySortPosition[i].order = itemsBySortPosition[i].order!! - 1
    }
    val position = this.findById(item.id)!!.first
    this.removeItemAt(position)
}

fun ListItemSortedList.moveItemRange(
    fromIndex: Int,
    itemCount: Int,
    toIndex: Int,
    shiftCheckedItemOrders: Boolean,
    forceIsChild: Boolean? = null,
): Int? {
    if (fromIndex == toIndex || itemCount <= 0) return null

    this.beginBatchedUpdates()

    val isMoveUp = fromIndex < toIndex

    val fromOrder = get(fromIndex).order!!
    val toOrder = get(toIndex).order!!
    val insertOrder = if (isMoveUp) toOrder - itemCount + 1 else toOrder

    if (isMoveUp) {
        this.shiftItemOrders(
            fromOrder + itemCount until toOrder + 1,
            -itemCount,
            shiftCheckedItemOrders,
        )
    } else {
        this.shiftItemOrders(toOrder until fromOrder, itemCount, shiftCheckedItemOrders)
    }
    val itemsToMove =
        (0 until itemCount)
            .map { this[fromIndex + it] }
            .mapIndexed { index, item ->
                val movedItem = item
                movedItem.order = insertOrder + index
                movedItem
            }
    itemsToMove.firstOrNull()?.let {
        if (forceIsChild != null) {
            this.forceItemIsChild(it, forceIsChild, resetBefore = true)
        }
    }
    val itemIds = itemsToMove.map { it.id }
    // Have to recalc the childs positions first
    this.recalcPositions(itemIds.reversed())
    this.recalcPositions(itemIds)
    this.endBatchedUpdates()
    val newPosition = this.indexOfFirst { it.id == itemsToMove[0].id }!!
    return newPosition
}

fun ListItemSortedList.deleteItem(
    position: Int,
    childrenToDelete: List<ListItem>? = null,
): ListItem {
    this.beginBatchedUpdates()
    val item = this[position]
    val deletedItem = this[position].clone() as ListItem
    val children = childrenToDelete ?: item.children
    this.shiftItemOrders(position + children.size until this.size(), -(children.size + 1), true)
    (item + children).indices.forEach { this.removeItemAt(position) }
    this.endBatchedUpdates()
    return deletedItem
}

fun ListItemSortedList.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach {
        if (it.order!! >= threshold) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun ListItemSortedList.shiftItemOrders(
    orderRange: IntRange,
    valueToAdd: Int,
    shiftCheckedItemOrders: Boolean,
) {
    this.forEach {
        if (it.order!! in orderRange) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun List<ListItem>.shiftItemOrders(orderRange: IntRange, valueToAdd: Int) {
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

fun SortedList<ListItem>.shiftItemOrdersHigher(threshold: Int, valueToAdd: Int) {
    this.forEach {
        if (it.order!! > threshold) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun ListItemSortedList.toMutableList(): MutableList<ListItem> {
    return this.indices.map { this[it] }.toMutableList()
}

fun SortedList<ListItem>.toMutableList(): MutableList<ListItem> {
    return this.indices.map { this[it] }.toMutableList()
}

fun SortedList<ListItem>.cloneList(): MutableList<ListItem> {
    return toMutableList().cloneList()
}

fun ListItemSortedList.cloneList(): MutableList<ListItem> {
    return this.indices.map { this[it].clone() as ListItem }.toMutableList()
}

fun List<ListItem>.cloneList(): MutableList<ListItem> {
    return this.indices.map { this[it].clone() as ListItem }.toMutableList()
}

fun ListItemSortedList.setIsChild(
    position: Int,
    isChild: Boolean,
    forceOnChildren: Boolean = false,
    forceNotify: Boolean = false,
) {
    if (position == 0 && isChild) {
        return
    }
    if (forceOnChildren) {
        this.setIsChild((position..position + this[position].children.size).toList(), isChild)
    } else {
        val item = this[position].clone() as ListItem
        val valueChanged = item.isChild != isChild
        if (valueChanged || forceNotify) {
            item.isChild = isChild
            this.updateItemAt(position, item)
            if (!item.isChild) {
                this.recalcPositions(item.children.reversed().map { it.id })
            }
        }
    }
}

fun ListItemSortedList.setIsChild(positions: List<Int>, isChild: Boolean) {
    val changedPositions = mutableListOf<Int>()
    val items = this.cloneList()
    positions.forEach {
        val item = items[it]
        if (item.isChild != isChild) {
            changedPositions.add(it)
            item.isChild = isChild
        }
    }
    updatePositions(changedPositions, items)
}

fun ListItemSortedList.setChecked(
    position: Int,
    checked: Boolean,
    recalcChildrenPositions: Boolean = false,
): Int {
    val item = this[position].clone() as ListItem
    if (item.checked != checked) {
        item.checked = checked
    }
    //    this.beginBatchedUpdates() // TODO: less notifies?
    val (_, changedPositionsAfterSort) = this.setChecked(listOf(position), checked, false)
    if (recalcChildrenPositions) {
        val children = if (checked) item.children.reversed() else item.children
        //        children.com.philkes.notallyx.recyclerview.forEach { child ->
        //
        // this.recalculatePositionOfItemAt(this.com.philkes.notallyx.recyclerview.findById(child.id)!!.first)
        //        }
        recalcPositions(children.map { it.id })
    }
    //    this.endBatchedUpdates()
    return changedPositionsAfterSort[0]
}

fun ListItemSortedList.setChecked(
    positions: Collection<Int>,
    checked: Boolean,
    recalcChildrenPositions: Boolean = false,
): Pair<List<Int>, List<Int>> {
    val changedPositions = mutableListOf<Int>()
    val items = this.cloneList()
    positions.forEach {
        val item = items[it]
        if (item.checked != checked) {
            changedPositions.add(it)
            item.checked = checked
        }
    }
    val changedPositionsAfterSort =
        updatePositions(changedPositions, items, recalcChildrenPositions)
    return Pair(changedPositions, changedPositionsAfterSort)
}

/**
 * Checks item at position and its children
 *
 * @return The position of the checked item afterwards
 */
fun ListItemSortedList.setCheckedWithChildren(position: Int, checked: Boolean): Int {
    val parent = this[position]
    val positionsWithChildren =
        (position..position + parent.children.size)
            .reversed() // children have to be checked first for correct sorting
            .toList()

    val (_, changedPositionsAfterSort) = this.setChecked(positionsWithChildren, checked, true)
    return changedPositionsAfterSort.reversed()[0]
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

fun ListItemSortedList.reversed(): List<ListItem> {
    return toMutableList().reversed()
}

private fun ListItemSortedList.updatePositions(
    changedPositions: MutableList<Int>,
    updatedItems: MutableList<ListItem>,
    recalcChildrenPositions: Boolean = false,
): List<Int> {
    this.beginBatchedUpdates()
    val idsOfChildren = mutableSetOf<Int>()
    changedPositions.forEach {
        val updatedItem = updatedItems[it]
        val newPosition = this.indexOfFirst { item -> item.id == updatedItem.id }!!
        if (!updatedItem.isChild) {
            idsOfChildren.addAll(
                updatedItem.children
                    .reversed() // start recalculations from the lowest child upwards
                    .map { item -> item.id }
            )
        }
        this.updateItemAt(newPosition, updatedItem)
    }
    if (recalcChildrenPositions) {
        //        idsOfChildren.com.philkes.notallyx.recyclerview.forEach { childId ->
        //
        // this.recalculatePositionOfItemAt(this.com.philkes.notallyx.recyclerview.findById(childId)!!.first)
        //        }
        recalcPositions(idsOfChildren)
    }

    val changedPositionsAfterSort =
        changedPositions
            .map { pos -> this.indexOfFirst { item -> item.id == updatedItems[pos].id }!! }
            .toList()
    this.endBatchedUpdates()
    return changedPositionsAfterSort
}

fun SortedList<ListItem>.recalcPositions(itemIds: Collection<Int> = this.map { it.id }) {
    itemIds.forEach { id -> this.recalculatePositionOfItemAt(this.findById(id)!!.first) }
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
    removeAll(item + item.children)
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
    item.children.forEach { remove(it) }
    remove(item)
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
