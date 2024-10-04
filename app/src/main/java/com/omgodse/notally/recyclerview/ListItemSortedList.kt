package com.omgodse.notally.recyclerview

import androidx.recyclerview.widget.SortedList
import com.omgodse.notally.room.ListItem

class ListItemSortedList(callback: Callback<ListItem>) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    override fun updateItemAt(index: Int, item: ListItem?) {
        updateChildStatus(item, index)
        super.updateItemAt(index, item)
        if (item?.isChild == false) {
            item.children = item.children.map { findById(it.id)!!.second }.toMutableList()
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

    override fun add(item: ListItem?): Int {
        val position = super.add(item)
        if (item?.isChild == true) {
            updateChildInParent(position, item)
        }
        return position
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

    fun add(item: ListItem, isChild: Boolean?) {
        if (isChild != null) {
            if (item.isChild != isChild) {
                if (!item.isChild && isChild) {
                    item.children.clear()
                }
                item.isChild = isChild
            }
            if (item.isChild) {
                updateChildInParent(item.order!!, item)
            }
        }
        add(item) // TODO: can skip the isChild update in other method
    }

    override fun removeItemAt(index: Int): ListItem {
        val item = this[index]
        val removedItem = super.removeItemAt(index)
        if (item?.isChild == true) {
            removeChildFromParent(item)
        }
        return removedItem
    }

    private fun removeChildFromParent(item: ListItem) {
        findParent(item)?.let { (_, parent) ->
            val childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            parent.children.removeAt(childIndex)
        }
    }

    private fun updateChildInParent(position: Int, item: ListItem) {
        var childIndex: Int? = null
        var parentInfo = findParent(item)
        var parent: ListItem? = null
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
    fun findLastIsNotChild(index: Int): Int? {
        var position = index
        while (this[position].isChild) {
            if (position < 0) {
                return null
            }
            position--
        }
        return position
    }
}
