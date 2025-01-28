package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.deepCopy

class ListItemSortedList(private val callback: Callback<ListItem>) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    override fun updateItemAt(index: Int, item: ListItem?) {
        updateChildStatus(item, index)
        super.updateItemAt(index, item)
        if (item?.isChild == false) {
            item.children = item.children.map { findById(it.id)!!.second }.toMutableList()
        }
    }

    override fun add(item: ListItem?): Int {
        val position = super.add(item)
        if (item?.isChild == true) {
            updateChildInParent(position, item)
        }
        return position
    }

    fun add(item: ListItem, isChild: Boolean?) {
        if (isChild != null) {
            forceItemIsChild(item, isChild)
        }
        add(item)
    }

    fun forceItemIsChild(item: ListItem, newValue: Boolean, resetBefore: Boolean = false) {
        if (resetBefore) {
            if (item.isChild) {
                // In this case it was already a child and moved to other position,
                // therefore reset the child association
                removeChildFromParent(item)
                item.isChild = false
            }
        }
        if (item.isChild != newValue) {
            if (!item.isChild) {
                item.children.clear()
            } else {
                removeChildFromParent(item)
            }
            item.isChild = newValue
        }
        if (item.isChild) {
            updateChildInParent(item.order!!, item)
        }
    }

    override fun removeItemAt(index: Int): ListItem {
        val item = this[index]
        val removedItem = super.removeItemAt(index)
        if (item?.isChild == true) {
            removeChildFromParent(item)
        }
        return removedItem
    }

    fun init(items: Collection<ListItem>, resetIds: Boolean = true) {
        beginBatchedUpdates()
        super.clear()
        val initializedItems = items.deepCopy()
        initList(initializedItems, resetIds)
        if (callback is ListItemSortedByCheckedCallback) {
            val (children, parents) = initializedItems.partition { it.isChild }
            // Need to use replaceAll for auto-sorting checked items
            super.replaceAll(parents.toTypedArray(), false)
            super.addAll(children.toTypedArray(), false)
        } else {
            super.addAll(initializedItems.toTypedArray(), false)
        }
        endBatchedUpdates()
    }

    private fun separateChildrenFromParent(item: ListItem) {
        findParent(item)?.let { (_, parent) ->
            val childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            // If a child becomes a parent it inherits its children below it
            val separatedChildren =
                if (childIndex < parent.children.lastIndex)
                    parent.children.subList(childIndex + 1, parent.children.size)
                else listOf()
            item.children.clear()
            item.children.addAll(separatedChildren)
            while (parent.children.size >= childIndex + 1) {
                parent.children.removeAt(childIndex)
            }
        }
    }

    private fun updateChildStatus(item: ListItem?, index: Int) {
        val wasChild = this[index].isChild
        if (item?.isChild == true) {
            updateChildInParent(index, item)
        } else if (wasChild && item?.isChild == false) {
            // Child becomes parent
            separateChildrenFromParent(item)
        }
    }

    fun removeChildFromParent(item: ListItem) {
        findParent(item)?.let { (_, parent) ->
            val childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            parent.children.removeAt(childIndex)
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

    fun updateChildInParent(position: Int, item: ListItem) {
        val childIndex: Int?
        val parentInfo = findParent(item)
        val parent: ListItem?
        if (parentInfo == null) {
            val parentPosition = findLastIsNotChild(position - 1)!!
            childIndex = position - parentPosition - 1
            parent = this[parentPosition]
        } else {
            parent = parentInfo.second
            childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            parent.children.removeAt(childIndex)
        }
        parent!!.children.add(childIndex, item)
        parent.children.addAll(childIndex + 1, item.children)
        item.children.clear()
    }

    /** @return position of the found item and its difference to index */
    private fun findLastIsNotChild(index: Int): Int? {
        if (index < 0) return null
        for (i in index downTo 0) {
            if (!this[i].isChild) {
                return i
            }
        }
        return null
    }
}
