package com.philkes.notallyx.presentation.view.note.listitem.sorting

import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.findChild
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
    val insertPosition = if (isMoveUp) toIndex - itemCount + 1 else toIndex

    if (isMoveUp) {
        this.shiftItemOrders(
            fromIndex + itemCount until toIndex + 1,
            -itemCount,
            shiftCheckedItemOrders,
        )
    } else {
        this.shiftItemOrders(toIndex until fromIndex, itemCount, shiftCheckedItemOrders)
    }

    val itemsToMove =
        (0 until itemCount)
            .map { this[fromIndex + it] }
            .mapIndexed { index, item ->
                val movedItem = item.clone() as ListItem
                movedItem.order = insertPosition + index
                movedItem
            }
    itemsToMove.forEach { listItem ->
        this.updateItemAt(this.findById(listItem.id)!!.first, listItem)
    }
    itemsToMove.forEach {
        if (forceIsChild != null) {
            val (_, item) = this.findById(it.id)!!
            this.forceItemIsChild(item, forceIsChild, resetBefore = true)
            itemsToMove.forEach { listItem ->
                this.updateItemAt(this.findById(listItem.id)!!.first, listItem)
            }
        }
    }
    this.recalcPositions(
        itemsToMove.reversed().map { it.id }
    ) // make sure children are at correct positions
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

fun ListItemSortedList.shiftItemOrders(
    positionRange: IntRange,
    valueToAdd: Int,
    shiftCheckedItemOrders: Boolean,
) {
    this.forEach {
        if (it.order!! in positionRange && (shiftCheckedItemOrders || !it.checked)) {
            it.order = it.order!! + valueToAdd
        }
    }
}

fun ListItemSortedList.toMutableList(): MutableList<ListItem> {
    return this.indices.map { this[it] }.toMutableList()
}

fun ListItemSortedList.cloneList(): MutableList<ListItem> {
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

fun ListItemSortedList.findById(id: Int): Pair<Int, ListItem>? {
    val position = this.indexOfFirst { it.id == id } ?: return null
    return Pair(position, this[position])
}

fun ListItemSortedList.toReadableString(): String {
    return map { "$it order: ${it.order} id: ${it.id}" }.joinToString("\n")
}

fun ListItemSortedList.findParent(childItem: ListItem): Pair<Int, ListItem>? {
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

fun ListItemSortedList.recalcPositions(itemIds: Collection<Int> = this.map { it.id }) {
    itemIds.forEach { id -> this.recalculatePositionOfItemAt(this.findById(id)!!.first) }
}

fun <R> ListItemSortedList.map(transform: (ListItem) -> R): List<R> {
    return (0 until this.size()).map { transform.invoke(this[it]) }
}

fun <R> ListItemSortedList.mapIndexed(transform: (Int, ListItem) -> R): List<R> {
    return (0 until this.size()).mapIndexed { idx, it -> transform.invoke(idx, this[it]) }
}

fun ListItemSortedList.forEach(function: (item: ListItem) -> Unit) {
    return (0 until this.size()).forEach { function.invoke(this[it]) }
}

fun ListItemSortedList.forEachIndexed(function: (idx: Int, item: ListItem) -> Unit) {
    for (i in 0 until this.size()) {
        function.invoke(i, this[i])
    }
}

fun ListItemSortedList.filter(function: (item: ListItem) -> Boolean): List<ListItem> {
    val list = mutableListOf<ListItem>()
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            list.add(this[i])
        }
    }
    return list.toList()
}

fun ListItemSortedList.find(function: (item: ListItem) -> Boolean): ListItem? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return this[i]
        }
    }
    return null
}

fun ListItemSortedList.indexOfFirst(function: (item: ListItem) -> Boolean): Int? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return i
        }
    }
    return null
}

val ListItemSortedList.lastIndex: Int
    get() = this.size() - 1

val ListItemSortedList.indices: IntRange
    get() = (0 until this.size())

fun ListItemSortedList.isNotEmpty(): Boolean {
    return size() > 0
}

fun ListItemSortedList.isEmpty(): Boolean {
    return size() == 0
}
