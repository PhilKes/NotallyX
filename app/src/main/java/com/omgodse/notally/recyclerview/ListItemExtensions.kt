package com.omgodse.notally.recyclerview

import androidx.recyclerview.widget.SortedList
import com.omgodse.notally.room.ListItem

fun ListItemSortedList.addItem(position: Int, item: ListItem) {
    item.sortingPosition = position
    this.add(item)
}

fun ListItemSortedList.moveItemRange(
    fromIndex: Int,
    itemCount: Int,
    toIndex: Int,
    forceIsChild: Boolean? = null,
): Int? {
    if (fromIndex == toIndex || itemCount <= 0) return null

    this.beginBatchedUpdates()

    val insertPosition = if (fromIndex < toIndex) toIndex - itemCount + 1 else toIndex

    if (fromIndex < toIndex) {
        this.addToSortingPositions(fromIndex + itemCount until toIndex + 1, -itemCount)
    } else {
        this.addToSortingPositions(toIndex until fromIndex, itemCount)
    }

    val itemsToMove = (0 until itemCount).map { this.removeItemAt(fromIndex) }

    itemsToMove.forEachIndexed { index, item ->
        val movedItem = item.clone() as ListItem
        movedItem.sortingPosition = insertPosition + index
        this.add(movedItem, forceIsChild)
    }

    this.endBatchedUpdates()
    val newPosition = this.indexOfFirst { it.id == itemsToMove[0].id }!!
    return newPosition
}

// fun ListItemSortedList.addAndNotify(
//    position: Int,
//    item: ListItem,
//    adapter: RecyclerView.Adapter<*>,
// ) {
//    if (item.checked && item.sortingPosition == null) {
//        item.sortingPosition = position
//    }
//    add(position, item)
//    adapter.notifyItemInserted(position)
// }

fun ListItemSortedList.deleteItem(
    position: Int,
    childrenToDelete: List<ListItem>? = null,
): ListItem {
    this.beginBatchedUpdates()
    val item = this[position]
    val deletedItem = this[position].clone() as ListItem
    val children = childrenToDelete ?: item.children
    //    for (i in position + children.size until this.size()) {
    //        this[i].sortingPosition = this[i].sortingPosition!! - (children.size + 1)
    //    }
    this.addToSortingPositions(position + children.size until this.size(), -(children.size + 1))
    (item + children).indices.forEach { this.removeItemAt(position) }
    this.endBatchedUpdates()
    return deletedItem
}

fun ListItemSortedList.addToSortingPositions(positionRange: IntRange, valueToAdd: Int) {
    this.forEach {
        if (it.sortingPosition!! in positionRange) {
            it.sortingPosition = it.sortingPosition!! + valueToAdd
        }
    }
}

fun <T, R> SortedList<T>.map(transform: (T) -> R): List<R> {
    return (0 until this.size()).map { transform.invoke(this[it]) }
}

fun <T> SortedList<T>.forEach(function: (item: T) -> Unit) {
    return (0 until this.size()).forEach { function.invoke(this[it]) }
}

fun <T> SortedList<T>.forEachIndexed(function: (idx: Int, item: T) -> Unit) {
    for (i in 0 until this.size()) {
        function.invoke(i, this[i])
    }
}

fun <T> SortedList<T>.filter(function: (item: T) -> Boolean): List<T> {
    val list = mutableListOf<T>()
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            list.add(this[i])
        }
    }
    return list.toList()
}

fun <T> SortedList<T>.find(function: (item: T) -> Boolean): T? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return this[i]
        }
    }
    return null
}

fun <T> SortedList<T>.indexOfFirst(function: (item: T) -> Boolean): Int? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return i
        }
    }
    return null
}

val <T> SortedList<T>.lastIndex: Int
    get() = this.size() - 1

val <T> SortedList<T>.indices: IntRange
    get() = (0 until this.size())

fun <T> SortedList<T>.isNotEmpty(): Boolean {
    return size() > 0
}

fun <T> SortedList<T>.isEmpty(): Boolean {
    return size() == 0
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
) {
    if (forceOnChildren) {
        this.setIsChild((position..position + this[position].children.size).toList(), isChild)
    } else {
        val item = this[position].clone() as ListItem
        if (item.isChild != isChild) {
            item.isChild = isChild
            this.updateItemAt(position, item)
            //            this.updateAllChildren()
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

fun ListItemSortedList.setChecked(position: Int, checked: Boolean): Int {
    val item = this[position].clone() as ListItem
    if (item.checked != checked) {
        item.checked = checked
        this.updateItemAt(position, item)
        return this.indexOf(item)
    }
    return position
}

fun ListItemSortedList.setChecked(
    positions: Collection<Int>,
    checked: Boolean,
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
    val changedPositionsAfterSort = updatePositions(changedPositions, items)
    return Pair(changedPositions, changedPositionsAfterSort)
}

/**
 * Checks item at position and its children
 *
 * @return The position of the checked item afterwards
 */
fun ListItemSortedList.setCheckedWithChildren(position: Int, checked: Boolean): Int {
    val parent = this[position]
    var childrenWithSameChecked = 0
    parent.children.forEach {
        val updatedChild = this.find { item -> item.id == it.id }!!
        if (updatedChild.checked != checked) {
            childrenWithSameChecked++
        }
    }
    val positionsWithChildren = (position..position + childrenWithSameChecked).toList()

    val (_, changedPositionsAfterSort) = this.setChecked(positionsWithChildren, checked)
    return changedPositionsAfterSort[0]
}

operator fun ListItem.plus(list: List<ListItem>): List<ListItem> {
    return mutableListOf(this) + list
}

fun ListItemSortedList.toReadableString(): String {
    return map { "${it.toString()} sortingPosition: ${it.sortingPosition} id: ${it.id}" }
        .joinToString("\n")
}

fun Collection<ListItem>.toReadableString(): String {
    return map { "${it.toString()} uncheckedPos: ${it.sortingPosition} id: ${it.id}" }
        .joinToString("\n")
}

fun ListItemSortedList.findParent(childItem: ListItem): Pair<Int, ListItem>? {
    this.indices.forEach {
        if (this[it].children.find { child -> child.id == childItem.id } != null) {
            return Pair(it, this[it])
        }
    }
    return null
}

private fun ListItemSortedList.updatePositions(
    changedPositions: MutableList<Int>,
    items: MutableList<ListItem>,
): List<Int> {
    this.beginBatchedUpdates()
    changedPositions.forEach {
        val updatedItem = items[it]
        val newPosition = this.indexOfFirst { item -> item.id == updatedItem.id }!!
        this.updateItemAt(newPosition, updatedItem)
    }
    val changedPositionsAfterSort =
        changedPositions
            .map { pos -> this.indexOfFirst { item -> item.id == items[pos].id }!! }
            .toList()
    this.endBatchedUpdates()
    return changedPositionsAfterSort
}
