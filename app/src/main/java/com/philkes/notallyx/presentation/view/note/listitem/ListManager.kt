package com.philkes.notallyx.presentation.view.note.listitem

import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.areAllChecked
import com.philkes.notallyx.data.model.plus
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.deleteItem
import com.philkes.notallyx.presentation.view.note.listitem.sorting.filter
import com.philkes.notallyx.presentation.view.note.listitem.sorting.findById
import com.philkes.notallyx.presentation.view.note.listitem.sorting.findParent
import com.philkes.notallyx.presentation.view.note.listitem.sorting.isNotEmpty
import com.philkes.notallyx.presentation.view.note.listitem.sorting.lastIndex
import com.philkes.notallyx.presentation.view.note.listitem.sorting.moveItemRange
import com.philkes.notallyx.presentation.view.note.listitem.sorting.reversed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.setChecked
import com.philkes.notallyx.presentation.view.note.listitem.sorting.setCheckedWithChildren
import com.philkes.notallyx.presentation.view.note.listitem.sorting.setIsChild
import com.philkes.notallyx.presentation.view.note.listitem.sorting.shiftItemOrders
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toReadableString
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.changehistory.ChangeCheckedForAllChange
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.DeleteCheckedChange
import com.philkes.notallyx.utils.changehistory.ListAddChange
import com.philkes.notallyx.utils.changehistory.ListCheckedChange
import com.philkes.notallyx.utils.changehistory.ListDeleteChange
import com.philkes.notallyx.utils.changehistory.ListEditTextChange
import com.philkes.notallyx.utils.changehistory.ListIsChildChange
import com.philkes.notallyx.utils.changehistory.ListMoveChange

/**
 * Should be used for all changes to the items list. Notifies the [RecyclerView.Adapter] and pushes
 * according changes to the [ChangeHistory]
 */
class ListManager(
    private val recyclerView: RecyclerView,
    private val changeHistory: ChangeHistory,
    private val preferences: NotallyXPreferences,
    private val inputMethodManager: InputMethodManager,
) {

    private var nextItemId: Int = 0
    private lateinit var items: ListItemSortedList
    internal lateinit var adapter: RecyclerView.Adapter<ListItemVH>

    fun add(
        position: Int = items.size(),
        item: ListItem = defaultNewItem(position),
        pushChange: Boolean = true,
    ) {
        (item + item.children).forEach { setIdIfUnset(it) }
        val itemBeforeInsert = item.clone() as ListItem

        items.beginBatchedUpdates()
        for ((idx, newItem) in (item + item.children).withIndex()) {
            addItem(position + idx, newItem)
        }
        items.endBatchedUpdates()

        if (pushChange) {
            changeHistory.push(ListAddChange(position, item.id, itemBeforeInsert, this))
        }
        val positionAfterAdd = items.findById(item.id)!!.first
        recyclerView.post {
            val viewHolder =
                recyclerView.findViewHolderForAdapterPosition(positionAfterAdd) as ListItemVH?
            if (!item.checked && viewHolder != null) {
                viewHolder.focusEditText(inputMethodManager = inputMethodManager)
            }
        }
    }

    /**
     * Deletes item and its children at given position.
     *
     * @param force if false, deletion can be rejected, e.g. if trying to delete the first item
     * @param childrenToDelete can be used when a [ListAddChange] is undone to pass the item at the
     *   state before the insertion
     * @param allowFocusChange if true the UI will focus the last valid ListItem's EditText
     * @return the removed [ListItem] or null if deletion was rejected
     */
    fun delete(
        position: Int = items.lastIndex,
        force: Boolean = true,
        childrenToDelete: List<ListItem>? = null,
        pushChange: Boolean = true,
        allowFocusChange: Boolean = true,
    ): ListItem? {
        if (position < 0 || position > items.lastIndex) {
            return null
        }
        var item: ListItem? = null
        if (force || position > 0) {
            item = items.deleteItem(position, childrenToDelete)
        }
        if (!force && allowFocusChange) {
            if (position > 0) {
                this.moveFocusToNext(position - 2)
            } else if (items.size() > 1) {
                this.moveFocusToNext(position)
            }
        }
        if (item != null && pushChange) {
            changeHistory.push(ListDeleteChange(item.order!!, item, this))
        }
        return item
    }

    fun deleteById(
        itemId: Int,
        force: Boolean = true,
        childrenToDelete: List<ListItem>? = null,
        pushChange: Boolean = true,
        allowFocusChange: Boolean = true,
    ): ListItem? {
        return delete(
            items.findById(itemId)!!.first,
            force,
            childrenToDelete,
            pushChange,
            allowFocusChange,
        )
    }

    /** @return position of the moved item afterwards */
    fun move(
        positionFrom: Int,
        positionTo: Int,
        pushChange: Boolean = true,
        updateChildren: Boolean = true,
        isDrag: Boolean = false,
    ): Int? {
        val itemTo = items[positionTo]
        val itemFrom = items[positionFrom]
        val itemBeforeMove = itemFrom.clone() as ListItem
        // Disallow move unchecked item under any checked item (if auto-sort enabled)
        if (isAutoSortByCheckedEnabled() && itemTo.checked || itemTo.isChildOf(itemFrom)) {
            return null
        }
        val checkChildPosition = if (positionTo < positionFrom) positionTo - 1 else positionTo
        val forceIsChild =
            when {
                isDrag ->
                    if (itemFrom.isChild && checkChildPosition.isBeforeChildItem) true else null
                positionTo == 0 && itemFrom.isChild -> false
                itemFrom.isChild -> true // if child is moved parent could change
                updateChildren && checkChildPosition.isBeforeChildItemOfOtherParent -> true
                else -> null
            }

        val newPosition =
            items.moveItemRange(
                positionFrom,
                itemFrom.itemCount,
                positionTo,
                forceIsChild = forceIsChild,
            ) ?: return null

        finishMove(
            positionFrom,
            positionTo,
            newPosition,
            itemBeforeMove,
            updateIsChild = false,
            updateChildren = false,
            pushChange,
        )
        return newPosition
    }

    fun finishMove(
        positionFrom: Int,
        positionTo: Int,
        newPosition: Int,
        itemBeforeMove: ListItem,
        updateIsChild: Boolean,
        updateChildren: Boolean,
        pushChange: Boolean,
    ) {
        if (updateIsChild) {
            if (newPosition.isBeforeChildItemOfOtherParent) {
                items.setIsChild(newPosition, isChild = true, forceOnChildren = true)
            } else if (newPosition == 0) {
                items.setIsChild(newPosition, false)
            }
        }
        if (updateChildren) {
            val item = items[newPosition]
            val forceValue = item.isChild
            items.forceItemIsChild(item, forceValue, resetBefore = true)
            items.updateItemAt(items.findById(item.id)!!.first, item)
        }
        if (pushChange) {
            changeHistory.push(
                ListMoveChange(positionFrom, positionTo, newPosition, itemBeforeMove, this)
            )
        }
    }

    fun undoMove(positionAfter: Int, positionFrom: Int, itemBeforeMove: ListItem) {
        val actualPositionTo =
            if (positionAfter < positionFrom) {
                positionFrom + itemBeforeMove.children.size
            } else {
                positionFrom
            }
        val positionBefore =
            move(positionAfter, actualPositionTo, pushChange = false, updateChildren = false)!!
        if (items[positionBefore].isChild != itemBeforeMove.isChild) {
            items.setIsChild(positionBefore, itemBeforeMove.isChild)
        }
    }

    fun changeText(
        editText: EditText,
        listener: TextWatcher,
        position: Int,
        textBefore: String,
        textAfter: String,
        pushChange: Boolean = true,
    ) {
        val item = items[position]
        item.body = textAfter
        if (pushChange) {
            changeHistory.push(
                ListEditTextChange(editText, position, textBefore, textAfter, listener, this)
            )
        }
    }

    fun changeChecked(position: Int, checked: Boolean, pushChange: Boolean = true) {
        val item = items[position]
        if (item.checked == checked) {
            return
        }
        if (item.isChild) {
            changeCheckedForChild(checked, item, pushChange, position)
            return
        }
        items.setCheckedWithChildren(position, checked)
        if (pushChange) {
            changeHistory.push(ListCheckedChange(checked, item.id, this))
        }
    }

    fun changeCheckedById(id: Int, checked: Boolean, pushChange: Boolean = true) {
        changeChecked(items.findById(id)!!.first, checked, pushChange)
    }

    private fun changeCheckedForChild(
        checked: Boolean,
        item: ListItem,
        pushChange: Boolean,
        position: Int,
    ) {
        var actualPosition = position
        val (parentPosition, parent) = items.findParent(item)!!
        if (!checked) {
            // If a child is being unchecked and the parent was checked, the parent gets unchecked
            // too
            if (parent.checked) {
                items.setChecked(parentPosition, false, recalcChildrenPositions = true)
                actualPosition = items.findById(item.id)!!.first
            }
        }
        items.setChecked(actualPosition, checked)
        if (parent.children.areAllChecked() && !parent.checked) {
            items.setChecked(parentPosition, true, recalcChildrenPositions = true)
        }
        if (pushChange) {
            changeHistory.push(ListCheckedChange(checked, item.id, this))
        }
    }

    fun changeCheckedForAll(checked: Boolean, pushChange: Boolean = true) {
        val parentIds = mutableListOf<Int>()
        val changedIds = mutableListOf<Int>()
        items
            .reversed() // have to start from the bottom upwards, otherwise sort order will be wrong
            .forEach { item ->
                if (!item.isChild) {
                    parentIds.add(item.id)
                }
                if (item.checked != checked) {
                    changedIds.add(item.id)
                }
            }
        parentIds.forEach {
            val (position, _) = items.findById(it)!!
            changeChecked(position, checked, pushChange = false)
        }
        if (pushChange) {
            changeHistory.push(ChangeCheckedForAllChange(checked, changedIds, this))
        }
    }

    fun checkByIds(
        checked: Boolean,
        ids: Collection<Int>,
        recalcChildrenPositions: Boolean = false,
    ): Pair<List<Int>, List<Int>> {
        return check(checked, ids.map { items.findById(it)!!.first }, recalcChildrenPositions)
    }

    fun changeIsChild(position: Int, isChild: Boolean, pushChange: Boolean = true) {
        items.setIsChild(position, isChild)
        if (pushChange) {
            changeHistory.push(ListIsChildChange(isChild, position, this))
        }
    }

    fun moveFocusToNext(position: Int) {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position + 1) as ListItemVH?
        if (viewHolder != null) {
            if (viewHolder.binding.CheckBox.isChecked) {
                moveFocusToNext(position + 1)
            } else viewHolder.binding.EditText.requestFocus()
        } else add(pushChange = false)
    }

    fun deleteCheckedItems(pushChange: Boolean = true) {
        val itemsToDelete =
            items.filter { it.checked }.map { it.clone() as ListItem }.sortedBy { it.isChild }
        items.beginBatchedUpdates()
        itemsToDelete
            .reversed() // delete children first so sorting works properly
            .forEach { items.deleteItem(it) }
        val deletedItems =
            itemsToDelete.toMutableList().filter { item ->
                // If a parent with its children was deleted, remove the children item
                // since DeleteCheckedChange uses listManager.add, which already adds the children
                // from parent.children list
                !(item.isChild &&
                    itemsToDelete.any { parent -> parent.children.any { it.id == item.id } })
            }
        items.endBatchedUpdates()
        if (pushChange) {
            changeHistory.push(DeleteCheckedChange(deletedItems, this))
        }
    }

    fun initList(items: ListItemSortedList) {
        this.items = items
        nextItemId = this.items.size()
        Log.d(TAG, "initList:\n${this.items.toReadableString()}")
    }

    internal fun getItem(position: Int): ListItem {
        return items[position]
    }

    internal fun defaultNewItem(position: Int) =
        ListItem(
            "",
            false,
            items.isNotEmpty() &&
                ((position < items.size() && items[position].isChild) ||
                    (position > 0 && items[position - 1].isChild)),
            null,
            mutableListOf(),
            nextItemId++,
        )

    private fun check(
        checked: Boolean,
        positions: Collection<Int>,
        recalcChildrenPositions: Boolean = false,
    ): Pair<List<Int>, List<Int>> {
        return items.setChecked(positions, checked, recalcChildrenPositions)
    }

    private fun addItem(position: Int, newItem: ListItem) {
        setIdIfUnset(newItem)
        items.shiftItemOrders(position until items.size(), 1)
        newItem.order = position
        val forceIsChild =
            when {
                position == 0 -> false
                (position - 1).isBeforeChildItemOfOtherParent -> true
                newItem.isChild && items.findParent(newItem) == null -> true
                else -> null
            }
        items.add(newItem, forceIsChild)
    }

    private fun setIdIfUnset(newItem: ListItem) {
        if (newItem.id == -1) {
            newItem.id = nextItemId++
        }
    }

    private fun isAutoSortByCheckedEnabled() =
        preferences.listItemSorting.value == ListItemSort.AUTO_SORT_BY_CHECKED

    private val Int.isBeforeChildItemOfOtherParent: Boolean
        get() {
            if (this < 0) {
                return false
            }
            val item = items[this]
            return item.isNextItemChild(this) && !items[this + item.itemCount].isChildOf(this)
        }

    private val Int.isBeforeChildItem: Boolean
        get() {
            if (this < 0 || this > items.lastIndex - 1) {
                return false
            }
            return items[this + 1].isChild
        }

    private fun ListItem.isNextItemChild(position: Int): Boolean {
        return (position < items.size() - itemCount) && (items[position + this.itemCount].isChild)
    }

    private fun ListItem.isChildOf(otherPosition: Int): Boolean {
        return isChildOf(items[otherPosition])
    }

    companion object {
        private const val TAG = "ListManager"
    }
}
