package com.philkes.notallyx.presentation.view.note.listitem

import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.areAllChecked
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.plus
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.cloneList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.filter
import com.philkes.notallyx.presentation.view.note.listitem.sorting.findById
import com.philkes.notallyx.presentation.view.note.listitem.sorting.findParent
import com.philkes.notallyx.presentation.view.note.listitem.sorting.forEach
import com.philkes.notallyx.presentation.view.note.listitem.sorting.isEmpty
import com.philkes.notallyx.presentation.view.note.listitem.sorting.isNotEmpty
import com.philkes.notallyx.presentation.view.note.listitem.sorting.lastIndex
import com.philkes.notallyx.presentation.view.note.listitem.sorting.moveItemRange
import com.philkes.notallyx.presentation.view.note.listitem.sorting.setChecked
import com.philkes.notallyx.presentation.view.note.listitem.sorting.setIsChild
import com.philkes.notallyx.presentation.view.note.listitem.sorting.shiftItemOrdersHigher
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toReadableString
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.changehistory.ChangeCheckedForAllChange
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.DeleteCheckedChange
import com.philkes.notallyx.utils.changehistory.EditTextState
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
    private val inputMethodManager: InputMethodManager?,
    private val endSearch: (() -> Unit)?,
    val refreshSearch: ((refocusView: View?) -> Unit)?,
) {

    private var nextItemId: Int = 0
    private lateinit var items: ListItemSortedList
    private var itemsChecked: ListItemSortedList? = null

    fun add(
        position: Int = items.size(),
        item: ListItem = defaultNewItem(position.coerceAtMost(items.size())),
        pushChange: Boolean = true,
    ) {
        val stateBefore = getState()
        val actualPosition = position.coerceAtMost(items.size())
        endSearch?.invoke()
        (item + item.children).forEach { setIdIfUnset(it) }

        items.beginBatchedUpdates()
        //        for ((idx, newItem) in (item + item.children).withIndex()) {
        addItem(actualPosition, item)
        //        }
        items.endBatchedUpdates()

        if (pushChange) {
            changeHistory.push(ListAddChange(stateBefore, getState(), this))
        }

        val positionAfterAdd = items.findById(item.id)!!.first
        recyclerView.post {
            val viewHolder =
                recyclerView.findViewHolderForAdapterPosition(positionAfterAdd) as ListItemVH?
            if (!item.checked && viewHolder != null) {
                inputMethodManager?.let { viewHolder.focusEditText(inputMethodManager = it) }
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
        isFromCheckedList: Boolean = false,
        force: Boolean = true,
        pushChange: Boolean = true,
        allowFocusChange: Boolean = true,
    ): Boolean {
        endSearch?.invoke()
        val stateBefore = getState()
        var result = false
        if (
            position < 0 || position > (if (isFromCheckedList) itemsChecked!! else items).lastIndex
        ) {
            return false
        }
        if (force || position > 0) {
            delete(position, isFromCheckedList)
            result = true
        }
        if (!force && allowFocusChange) {
            if (position > 0) {
                this.moveFocusToNext(position - 2)
            } else if (items.size() > 1) {
                this.moveFocusToNext(position)
            }
        }
        if (pushChange && result) {
            changeHistory.push(ListDeleteChange(stateBefore, getState(), this))
        }
        return result
    }

    /** @return position of the moved item afterwards */
    fun move(
        positionFrom: Int,
        positionTo: Int,
        pushChange: Boolean = true,
        updateChildren: Boolean = true,
        isDrag: Boolean = false,
    ): Int? {
        endSearch?.invoke()
        val itemTo = items[positionTo]
        val itemFrom = items[positionFrom]
        //        val itemBeforeMove = itemFrom.clone() as ListItem
        val stateBefore = getState()
        // Disallow move unchecked item under any checked item (if auto-sort enabled)
        val autoSortByCheckedEnabled = isAutoSortByCheckedEnabled()
        if (autoSortByCheckedEnabled && itemTo.checked || itemTo.isChildOf(itemFrom)) {
            return null
        }
        val checkChildPosition = if (positionTo < positionFrom) positionTo - 1 else positionTo
        val forceIsChild =
            when {
                isDrag -> null
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
                shiftCheckedItemOrders = !autoSortByCheckedEnabled,
            )
        if (newPosition == null) return null

        finishMove(
            positionFrom,
            positionTo,
            newPosition,
            stateBefore,
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
        stateBefore: ListState,
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
        val item = items[newPosition]
        if (updateChildren) {
            val forceValue = item.isChild
            items.forceItemIsChild(item, forceValue, resetBefore = true)
            items.updateItemAt(items.findById(item.id)!!.first, item)
        } else if (item.isChild && newPosition > 0) {
            items.removeChildFromParent(item)
            items.updateChildInParent(newPosition, item)
        }
        if (positionTo == 0) {
            items.refreshItem(1)
        }
        if (pushChange) {
            changeHistory.push(ListMoveChange(stateBefore, getState(), this))
        }
    }

    fun setItems(state: ListState) {
        this.items.setItems(state.items)
        this.itemsChecked?.setItems(state.checkedItems!!)
    }

    fun changeText(
        editText: EditText,
        listener: TextWatcher,
        position: Int,
        value: EditTextState,
        before: EditTextState? = null,
        pushChange: Boolean = true,
    ) {
        //        if(!pushChange) {
        endSearch?.invoke()
        //        }
        val item = items[position]
        item.body = value.text.toString()
        if (pushChange) {
            changeHistory.push(
                ListEditTextChange(editText, position, before!!, value, listener, this)
            )
            // TODO: fix focus change

            // refreshSearch?.invoke(editText)
        }
    }

    private fun ListItemSortedList.changeChecked(
        checked: Boolean,
        item: ListItem,
        changeParentToo: Boolean,
    ) {
        if (item.isChild) {
            findParent(item)?.let { (_, parent) ->
                val updatedParent = parent.clone() as ListItem
                updatedParent.findChild(item.id)!!.checked = checked
                if (changeParentToo) {
                    updatedParent.checked = checked
                }
                addWithChildren(updatedParent)
            }
        } else {
            val updatedParent = item.clone() as ListItem
            updatedParent.checked = checked
            updatedParent.children.forEach { it.checked = checked }
            addWithChildren(updatedParent)
        }
    }

    private fun checkWithAutoSort(parent: ListItem) {
        items.removeWithChildren(parent)
        parent.checked = true
        parent.children.forEach { it.checked = true }
        itemsChecked!!.addWithChildren(parent)
    }

    private fun uncheckWithAutoSort(item: ListItem) {
        if (item.isChild) {
            val (_, parent) = itemsChecked!!.findParent(item)!!
            itemsChecked!!.removeWithChildren(parent)
            parent.findChild(item.id)!!.checked = false
            parent.checked = false
            items.addWithChildren(parent)
        } else {
            itemsChecked!!.removeWithChildren(item)
            item.checked = false
            item.children.forEach { it.checked = false }
            items.addWithChildren(item)
        }
    }

    fun changeChecked(
        position: Int,
        checked: Boolean,
        isFromCheckedList: Boolean = false,
        pushChange: Boolean = true,
    ) {
        val beforeState = getState()
        val list = if (isFromCheckedList) itemsChecked!! else items
        val item = list[position]
        if (item.checked == checked) {
            return
        }
        if (item.isChild) {
            changeCheckedChild(item, checked, isFromCheckedList)
        } else {
            changeCheckedParent(item, checked)
        }
        if (pushChange) {
            changeHistory.push(ListCheckedChange(beforeState, getState(), this))
        }
    }

    private fun changeCheckedParent(parent: ListItem, checked: Boolean) {
        if (checked) {
            // A parent from unchecked is checked
            if (isAutoSortByCheckedEnabled()) {
                checkWithAutoSort(parent)
            } else {
                items.changeChecked(true, parent, false)
            }
        } else {
            if (isAutoSortByCheckedEnabled()) {
                uncheckWithAutoSort(parent)
            } else {
                items.changeChecked(false, parent, false)
            }
        }
    }

    private fun changeCheckedChild(child: ListItem, checked: Boolean, isFromCheckedList: Boolean) {
        if (checked) {
            val (_, parent) = items.findParent(child)!!
            val checkParentToo = parent.children.areAllChecked(except = child) && !parent.checked
            if (isAutoSortByCheckedEnabled() && checkParentToo) {
                checkWithAutoSort(parent)
            } else {
                items.changeChecked(true, child, checkParentToo)
            }
        } else {
            if (isFromCheckedList) {
                uncheckWithAutoSort(child)
            } else {
                items.changeChecked(false, child, changeParentToo = true)
            }
        }
    }

    private fun ListItemSortedList.findParentIds(checked: Boolean): List<Int> {
        return filter { !it.isChild && it.checked == checked }.map { it.id }
    }

    fun changeCheckedForAll(checked: Boolean, pushChange: Boolean = true) {
        val stateBefore = getState()
        if (checked || !isAutoSortByCheckedEnabled()) {
            items.findParentIds(!checked).forEach { id ->
                val (position, _) = items.findById(id)!!
                changeChecked(position, checked, isFromCheckedList = false, pushChange = false)
            }
        } else {
            itemsChecked!!.findParentIds(true).forEach { id ->
                val (position, _) = itemsChecked!!.findById(id)!!
                changeChecked(position, false, isFromCheckedList = true, pushChange = false)
            }
        }
        if (pushChange) {
            changeHistory.push(ChangeCheckedForAllChange(stateBefore, getState(), this))
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

    private fun delete(position: Int, isFromCheckedList: Boolean) {
        if (isFromCheckedList) {
            delete(itemsChecked!![position], true)
        } else {
            delete(items[position], false)
        }
    }

    private fun delete(item: ListItem, isFromCheckedList: Boolean) {
        items.shiftItemOrdersHigher(item.order!!, -1)
        itemsChecked?.shiftItemOrdersHigher(item.order!!, -1)
        if (isFromCheckedList) {
            itemsChecked!!.removeWithChildren(item)
        } else {
            items.removeWithChildren(item)
        }
    }

    private fun ListItemSortedList.deleteCheckedItems() {
        beginBatchedUpdates()
        filter { it.checked }
            .sortedBy { it.isChild }
            .reversed() // delete children first so sorting works properly
            .forEach { delete(it, this == itemsChecked) }
        endBatchedUpdates()
    }

    fun deleteCheckedItems(pushChange: Boolean = true) {
        endSearch?.invoke()
        val stateBefore = getState()
        items.deleteCheckedItems()
        itemsChecked?.deleteCheckedItems()
        if (pushChange) {
            changeHistory.push(DeleteCheckedChange(stateBefore, getState(), this))
        }
    }

    fun initList(items: ListItemSortedList, itemsChecked: ListItemSortedList? = null) {
        this.items = items
        this.itemsChecked = itemsChecked
        nextItemId = this.items.size() + (this.itemsChecked?.size() ?: 0)
        Log.d(TAG, "initList:\n${this.items.toReadableString()}")
        this.itemsChecked?.let { Log.d(TAG, "itemsChecked:\n${it}") }
    }

    internal fun getItem(position: Int): ListItem {
        return items[position]
    }

    internal fun getState() = ListState(items.cloneList(), itemsChecked?.cloneList())

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
        val order =
            if (items.isEmpty()) {
                0
            } else if (position > items.lastIndex) {
                getItem(items.lastIndex).order!! + 1
            } else {
                getItem(position).order!!
            }

        items.shiftItemOrdersHigher(order, 1 + newItem.children.size)
        itemsChecked?.shiftItemOrdersHigher(order, 1 + newItem.children.size)

        newItem.order = order
        newItem.children.forEachIndexed { index, child -> child.order = order + index + 1 }
        val forceIsChild =
            when {
                position == 0 -> false
                (position - 1).isBeforeChildItemOfOtherParent -> true
                newItem.isChild && items.findParent(newItem) == null -> true
                else -> null
            }
        if (forceIsChild == true) {
            newItem.isChild = true
            val actualPosition = position.coerceAtMost(items.lastIndex)
            items.updateChildInParent(actualPosition, newItem, clearChildren = false)
            items.addWithChildren(newItem)
        } else {
            newItem.isChild = forceIsChild ?: newItem.isChild
            items.addWithChildren(newItem)
        }
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

    fun startDrag(position: Int) {
        items[position].apply {
            isDragged = true
            children.forEach { it.isDragged = true }
        }
    }

    fun endDrag() {
        items.forEach { it.isDragged = false }
    }

    companion object {
        private const val TAG = "ListManager"
    }
}

data class ListState(val items: List<ListItem>, val checkedItems: List<ListItem>?)
