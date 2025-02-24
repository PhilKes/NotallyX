package com.philkes.notallyx.presentation.view.note.listitem

import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.areAllChecked
import com.philkes.notallyx.data.model.check
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.findParentPosition
import com.philkes.notallyx.data.model.plus
import com.philkes.notallyx.data.model.toReadableString
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.addWithChildren
import com.philkes.notallyx.presentation.view.note.listitem.sorting.cloneList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.filter
import com.philkes.notallyx.presentation.view.note.listitem.sorting.findParent
import com.philkes.notallyx.presentation.view.note.listitem.sorting.lastIndex
import com.philkes.notallyx.presentation.view.note.listitem.sorting.mapIndexed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.removeWithChildren
import com.philkes.notallyx.presentation.view.note.listitem.sorting.shiftItemOrders
import com.philkes.notallyx.presentation.view.note.listitem.sorting.shiftItemOrdersHigher
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
    lateinit var adapter: ListItemAdapter
    var checkedAdapter: CheckedListItemAdapter? = null
    private var nextItemId: Int = 0
    private val items: MutableList<ListItem>
        get() = adapter.items

    private var itemsChecked: SortedList<ListItem>? = null

    fun add(
        position: Int = items.size,
        item: ListItem = defaultNewItem(position.coerceAtMost(items.size)),
        pushChange: Boolean = true,
    ) {
        // TODO
        val stateBefore = getState()

        val parentBefore =
            if (position <= items.lastIndex && items[position].isChild) {
                findParent(items[position])
            } else null
        val insertOrder =
            if (position < 1) {
                0
            } else if (position <= items.lastIndex) {
                items[position - 1].order!! + 1
            } else {
                items.last().order!! + 1
            }
        items.shiftItemOrdersHigher(insertOrder - 1, 1 + item.children.size)
        itemsChecked?.shiftItemOrdersHigher(insertOrder - 1, 1 + item.children.size)
        (item + item.children).forEach { setIdIfUnset(it) }
        item.order = insertOrder
        item.children.forEachIndexed { index, child -> child.order = insertOrder + 1 + index }

        val (insertPos, count) = items.addWithChildren(item)
        if (item.isChild) {
            items.findParentPosition(insertPos)?.let { parentPos ->
                items[parentPos].children.add(insertPos - parentPos - 1, item)
            }
        } else if (parentBefore != null) {
            val (parentPos, parent) = parentBefore
            val childrenBelow =
                parent.children.filterIndexed { idx, _ -> parentPos + idx + 1 > insertPos - 1 }
            parent.children.removeAll(childrenBelow)
            item.children.addAll(childrenBelow)
        }
        adapter.notifyItemRangeInserted(insertPos, count)
        items.notifyPreviousFirstItem(insertPos, count)
        if (pushChange) {
            changeHistory.push(ListAddChange(stateBefore, getState(), this))
        }

        recyclerView.post {
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(insertPos) as ListItemVH?
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
        // TODO
        //        endSearch?.invoke()
        val stateBefore = getState()
        val items = this.items.toMutableList()
        var result = false
        if (
            position < 0 ||
                position > (if (isFromCheckedList) itemsChecked!!.lastIndex else items.lastIndex)
        ) {
            return false
        }
        if (force || position > 0) {
            //            delete(position, isFromCheckedList)
            val item = if (isFromCheckedList) itemsChecked!![position] else items[position]
            val order = item.order!!
            items.shiftItemOrdersHigher(order - 1, 1 + item.children.size)
            itemsChecked?.shiftItemOrdersHigher(order - 1, 1 + item.children.size)
            if (isFromCheckedList) {
                if (item.isChild) {
                    val parent = itemsChecked!!.findParent(item)!!.second
                    parent.children.remove(item)
                }
                itemsChecked!!.removeWithChildren(item)
            } else {
                if (item.isChild) {
                    val parent = items.findParent(item)!!.second
                    parent.children.remove(item)
                    parent.updateParentChecked(items)
                }
                items.removeWithChildren(item)
            }
            result = true
            adapter.submitList(items)
        }
        if (!force && allowFocusChange) {
            if (position > 0) {
                this.moveFocusToNext(position - 2)
            } else if (items.size > 1) {
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
    ): Int {
        val stateBefore = getState()
        val list = items.toMutableList()
        val movedItem = list[positionFrom]
        // Do not allow to move parent into its own children
        if (
            !movedItem.isChild &&
                positionTo in (positionFrom..positionFrom + movedItem.children.size)
        ) {
            return -1
        }

        //        val parentBefore = if (movedItem.isChild) list.findParent(movedItem)!!.second else
        // null
        val itemCount = 1 + movedItem.children.size

        val isMoveUp = positionFrom < positionTo
        val fromOrder = list[positionFrom].order!!
        val toOrder = list[positionTo].order!!
        val insertOrder = if (isMoveUp) toOrder - itemCount + 1 else toOrder

        if (isMoveUp) {
            list.shiftItemOrders(fromOrder + itemCount until toOrder + 1, -itemCount)
            itemsChecked?.shiftItemOrders(fromOrder + itemCount until toOrder + 1, -itemCount)
            if (stateBefore.checkedItems != null) {
                stateBefore.checkedItems.shiftItemOrders(
                    fromOrder + itemCount until toOrder + 1,
                    -itemCount,
                )
            }
        } else {
            list.shiftItemOrders(toOrder until fromOrder, itemCount)
            itemsChecked?.shiftItemOrders(toOrder until fromOrder, itemCount)
            if (stateBefore.checkedItems != null) {
                stateBefore.checkedItems.shiftItemOrders(toOrder until fromOrder, itemCount)
            }
        }

        if (movedItem.isChild) {
            list.findParent(movedItem)?.let { (_, parent) -> parent.children.remove(movedItem) }
        }
        list.removeWithChildren(movedItem)

        (movedItem + movedItem.children).forEachIndexed { index, item ->
            item.order = insertOrder + index
        }
        val (insertIdx, _) = list.addWithChildren(movedItem)
        //        if (movedItem.isChild) {
        //            list.findParentPosition(insertIdx)?.let { parentPos ->
        //                list[parentPos].children.addAll(
        //                    insertIdx - parentPos - 1,
        //                    movedItem + movedItem.children
        //                )
        //            }
        //            movedItem.children.clear()
        //        }
        submitList(list)
        return insertIdx
        //        if (pushChange) {
        //            finishMove(movedItem, positionTo, parentBefore, stateBefore, true)
        //            changeHistory.push(ListMoveChange(stateBefore, getState(), this))
        //        }
    }

    private fun ListItem.updateParentChecked(
        items: MutableList<ListItem> = this@ListManager.items
    ) {
        if (shouldParentBeChecked()) {
            changeCheckedParent(this, true, changeChildren = true, items = items)
        }
        if (shouldParentBeUnchecked()) {
            changeCheckedParent(this, false, changeChildren = false, items = items)
        }
    }

    fun finishMove(
        item: ListItem,
        itemTo: ListItem,
        parentBefore: ListItem?,
        stateBefore: ListState,
        pushChange: Boolean,
    ) {
        val positionTo = items.indexOfFirst { it.id == item.id }
        val forceIsChild = (itemTo.isChild || itemTo.children.isNotEmpty()) && !item.isChild
        if (positionTo == 0) {
            item.isChild = false
            items.notifyPreviousFirstItem(0, 1)
        } else if (forceIsChild) {
            item.isChild = true
        }

        if (item.isChild) {
            items.findParent(item)?.let { (pos, parent) ->
                parent.children.removeWithChildren(item)
            }
            items.findParentPosition(positionTo)?.let { parentPos ->
                items[parentPos].children.addAll(positionTo - parentPos - 1, item + item.children)
            }
            item.children.clear()
        }

        if (item.isChild) {
            val (_, parent) = items.findParent(item)!!
            parent.updateParentChecked()
            parentBefore?.updateParentChecked()
        }
        if (forceIsChild || positionTo == 0) {
            adapter.notifyItemChanged(positionTo)
        }
        if (pushChange) {
            changeHistory.push(ListMoveChange(stateBefore, getState(), this))
        }
        // TODO
        //        if (updateIsChild) {
        //            if (newPosition.isBeforeChildItemOfOtherParent) {
        //                items.setIsChild(newPosition, isChild = true, forceOnChildren = true)
        //            } else if (newPosition == 0) {
        //                items.setIsChild(newPosition, false)
        //            }
        //            val parentAfter = items.findParent(newPosition)?.second
        //            if (parentAfter != null) {
        //                if (parentAfter.shouldParentBeUnchecked()) {
        //                    if (isAutoSortByCheckedEnabled()) {
        //                        uncheckWithAutoSort(parentAfter, uncheckChildren = false)
        //                    } else {
        //                        items.changeChecked(
        //                            false,
        //                            parentAfter,
        //                            changeParentToo = false,
        //                            changeChildren = false,
        //                        )
        //                    }
        //                }
        //            }
        //        }
        //        val item = items[newPosition]
        //        if (updateChildren) {
        //            val forceValue = item.isChild
        //            items.forceItemIsChild(item, forceValue, resetBefore = true)
        //            items.updateItemAt(items.findById(item.id)!!.first, item)
        //            if (parentBefore != null) {
        //                if (parentBefore.shouldParentBeChecked()) {
        //                    changeCheckedParent(parentBefore, true)
        //                }
        //            }
        //        } else if (item.isChild && newPosition > 0) {
        //            items.removeChildFromParent(item)
        //            items.updateChildInParent(newPosition, item)
        //        }
        //        if (positionTo == 0) {
        //            items.refreshItem(1)
        //        }
        //        if (pushChange) {
        //            changeHistory.push(ListMoveChange(stateBefore, getState(), this))
        //        }
    }

    fun setItems(state: ListState) {
        submitList(state.items)
        this.itemsChecked?.replaceAll(state.checkedItems!!)
    }

    fun changeText(
        position: Int,
        value: EditTextState,
        before: EditTextState? = null,
        pushChange: Boolean = true,
        editText: EditText?,
        listener: TextWatcher?,
    ) {
        //        if(!pushChange) {
        endSearch?.invoke()
        //        }
        val item = items[position]
        item.body = value.text.toString()
        if (pushChange) {
            changeHistory.push(
                ListEditTextChange(editText!!, position, before!!, value, listener!!, this)
            )
            // TODO: fix focus change

            // refreshSearch?.invoke(editText)
        }
    }

    private fun ListItemSortedList.changeChecked(
        checked: Boolean,
        item: ListItem,
        changeParentToo: Boolean,
        changeChildren: Boolean = true,
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
            if (changeChildren) {
                updatedParent.children.forEach { it.checked = checked }
            }
            addWithChildren(updatedParent)
        }
    }

    private fun checkWithAutoSort(
        parent: ListItem,
        items: MutableList<ListItem> = this@ListManager.items,
    ) {
        val (pos, count) = items.removeWithChildren(parent)
        if (items == this@ListManager.items) {
            adapter.notifyItemRangeRemoved(pos, count)
            items.notifyPreviousFirstItem(pos, 0)
        }
        parent.check(true)
        itemsChecked!!.addWithChildren(parent)
    }

    private fun uncheckWithAutoSort(
        item: ListItem,
        uncheckChildren: Boolean = true,
        items: MutableList<ListItem> = this@ListManager.items,
    ) {
        if (item.isChild) {
            val (_, parent) = itemsChecked!!.findParent(item)!!
            itemsChecked!!.removeWithChildren(parent)
            parent.findChild(item.id)!!.checked = false
            parent.checked = false
            val (insertPos, count) = items.addWithChildren(parent)
            if (items == this@ListManager.items) {
                adapter.notifyItemRangeInserted(insertPos, count)
                items.notifyPreviousFirstItem(insertPos, count)
            }
        } else {
            itemsChecked!!.removeWithChildren(item)
            item.check(false, uncheckChildren)
            val (insertPos, count) = items.addWithChildren(item)
            if (items == this@ListManager.items) {
                adapter.notifyItemRangeInserted(insertPos, count)
                items.notifyPreviousFirstItem(insertPos, count)
            }
        }
    }

    private fun MutableList<ListItem>.notifyPreviousFirstItem(position: Int, count: Int) {
        if (position == 0 && size > count) {
            // To trigger enabling isChild swiping for the item that was previously at pos 0
            adapter.notifyItemChanged(count)
        }
    }

    fun changeChecked(
        position: Int,
        checked: Boolean,
        isFromCheckedList: Boolean = false,
        pushChange: Boolean = true,
    ) {
        val beforeState = getState()
        val item = if (isFromCheckedList) itemsChecked!![position] else items[position]
        if (item.checked == checked) {
            return
        }
        if (item.isChild) {
            changeCheckedChild(position, item, checked, isFromCheckedList)
        } else {
            changeCheckedParent(item, checked, changeChildren = true)
        }
        if (pushChange) {
            changeHistory.push(ListCheckedChange(beforeState, getState(), this))
        }
    }

    private fun changeCheckedParent(
        parent: ListItem,
        checked: Boolean,
        changeChildren: Boolean,
        items: MutableList<ListItem> = this@ListManager.items,
    ) {
        if (checked) {
            // A parent from unchecked is checked
            if (isAutoSortByCheckedEnabled()) {
                checkWithAutoSort(parent, items)
            } else {
                parent.check(true, checkChildren = changeChildren)
                if (items == this@ListManager.items) {
                    adapter.notifyListItemChanged(parent.id)
                }
            }
        } else {
            if (isAutoSortByCheckedEnabled()) {
                uncheckWithAutoSort(parent, uncheckChildren = changeChildren)
            } else {
                parent.check(false, checkChildren = changeChildren)
                if (items == this@ListManager.items) {
                    adapter.notifyListItemChanged(parent.id)
                }
            }
        }
    }

    private fun changeCheckedChild(
        position: Int,
        child: ListItem,
        checked: Boolean,
        isFromCheckedList: Boolean,
    ) {
        if (checked) {
            child.checked = true
            adapter.notifyItemChanged(position)
            val (_, parent) = items.findParent(child)!!
            parent.updateParentChecked()
        } else {
            if (isFromCheckedList) {
                uncheckWithAutoSort(child)
            } else {
                child.checked = false
                adapter.notifyItemChanged(position)
                child.checkParent(false)
            }
        }
    }

    private fun ListItem.checkParent(checked: Boolean) {
        val (parentPos, parent) = items.findParent(this)!!
        if (parent.checked != checked) {
            parent.checked = checked
            adapter.notifyItemChanged(parentPos)
        }
    }

    private fun ListItemSortedList.findParentIds(checked: Boolean): List<Int> {
        return filter { !it.isChild && it.checked == checked }.map { it.id }
    }

    private fun Collection<ListItem>.findParentIds(checked: Boolean): List<Int> {
        return filter { !it.isChild && it.checked == checked }.map { it.id }.distinct()
    }

    private fun Collection<ListItem>.findParentsByChecked(checked: Boolean): List<ListItem> {
        return filter { !it.isChild && it.checked == checked }.distinct()
    }

    private fun SortedList<ListItem>.findParentsByChecked(checked: Boolean): List<ListItem> {
        return filter { !it.isChild && it.checked == checked }.distinct()
    }

    fun changeCheckedForAll(checked: Boolean, pushChange: Boolean = true) {
        val stateBefore = getState()
        val parents =
            items.findParentsByChecked(!checked) +
                (itemsChecked?.findParentsByChecked(!checked) ?: listOf())
        parents.forEach { parent -> changeCheckedParent(parent, checked, true) }
        if (pushChange) {
            changeHistory.push(ChangeCheckedForAllChange(stateBefore, getState(), this))
        }
        // TODO
        //        val stateBefore = getState()
        //        if (checked || !isAutoSortByCheckedEnabled()) {
        //            items.findParentIds(!checked).forEach { id ->
        //                val (position, _) = items.findById(id)!!
        //                changeChecked(position, checked, isFromCheckedList = false, pushChange =
        // false)
        //            }
        //        } else {
        //            itemsChecked!!.findParentIds(true).forEach { id ->
        //                val (position, _) = itemsChecked!!.findById(id)!!
        //                changeChecked(position, false, isFromCheckedList = true, pushChange =
        // false)
        //            }
        //        }
        //        if (pushChange) {
        //            changeHistory.push(ChangeCheckedForAllChange(stateBefore, getState(), this))
        //        }
    }

    fun changeIsChild(position: Int, isChild: Boolean, pushChange: Boolean = true) {
        val stateBefore = getState()
        items.findParentPosition(position)?.let { parentPos ->
            val parent = items[parentPos]
            val item = items[position]
            item.isChild = isChild
            if (isChild) {
                parent.children.addAll(position - parentPos - 1, item + item.children)
                item.children.clear()
            } else {
                val childIndex = parent.children.indexOf(item)
                val childrenBelow = parent.children.filterIndexed { idx, _ -> idx > childIndex }
                parent.children.removeAll(childrenBelow)
                parent.children.remove(item)
                item.children = childrenBelow.toMutableList()
            }
        }

        if (pushChange) {
            changeHistory.push(ListIsChildChange(stateBefore, getState(), this))
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
        // TODO
        //        items.shiftItemOrdersHigher(item.order!!, -1)
        //        itemsChecked?.shiftItemOrdersHigher(item.order!!, -1)
        //        val parent = findParent(item)?.second
        //        if (isFromCheckedList) {
        //            itemsChecked!!.removeWithChildren(item)
        //        } else {
        //            items.removeWithChildren(item)
        //        }
        //        if (parent?.shouldParentBeChecked() == true) {
        //            changeCheckedParent(parent, true)
        //        }
    }

    private fun SortedList<ListItem>.deleteCheckedItems() {
        val isFromCheckedList = this == itemsChecked
        mapIndexed { index, listItem -> Pair(index, listItem) }
            .filter { it.second.checked }
            .sortedBy { it.second.isChild }
            .forEach {
                if (isFromCheckedList) {
                    itemsChecked!!.remove(it.second)
                } else {
                    items.remove(it.second)
                    adapter.notifyItemRemoved(it.first)
                }
            }
    }

    private fun MutableList<ListItem>.deleteCheckedItems() {
        val isFromCheckedList = this == itemsChecked
        mapIndexed { index, listItem -> Pair(index, listItem) }
            .filter { it.second.checked }
            .sortedBy { it.second.isChild }
            .forEach {
                if (isFromCheckedList) {
                    itemsChecked!!.remove(it.second)
                } else {
                    items.remove(it.second)
                    adapter.notifyItemRemoved(it.first)
                }
            }
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

    fun initList(
        items: MutableList<ListItem>,
        adapter: ListItemAdapter,
        itemsChecked: SortedList<ListItem>? = null,
        adapterChecked: CheckedListItemAdapter? = null,
    ) {
        //        this.items = items
        this.adapter = adapter
        this.itemsChecked = itemsChecked
        this.checkedAdapter = adapterChecked
        nextItemId = this.items.size + (this.itemsChecked?.size() ?: 0)
        Log.d(TAG, "initList:\n${this.items.toReadableString()}")
        this.itemsChecked?.let { Log.d(TAG, "itemsChecked:\n${it}") }
    }

    internal fun getItem(position: Int): ListItem {
        return items[position]
    }

    internal fun indexOf(item: ListItem): Int {
        return items.indexOf(item)
    }

    //    internal fun getState() = ListState(items.cloneList(), itemsChecked?.cloneList())
    // TODO
    internal fun getState() = ListState(items.cloneList(), itemsChecked?.cloneList())

    internal fun defaultNewItem(position: Int) =
        ListItem(
            "",
            false,
            items.isNotEmpty() &&
                ((position < items.size && items[position].isChild) ||
                    (position > 0 && items[position - 1].isChild)),
            null,
            mutableListOf(),
            nextItemId++,
        )

    //
    //    private fun check(
    //        checked: Boolean,
    //        positions: Collection<Int>,
    //        recalcChildrenPositions: Boolean = false,
    //    ): Pair<List<Int>, List<Int>> {
    //        return items.setChecked(positions, checked, recalcChildrenPositions)
    //    }

    //    private fun addItem(position: Int, newItem: ListItem) {
    //        setIdIfUnset(newItem)
    //        val order =
    //            if (items.isEmpty()) {
    //                0
    //            } else if (position > items.lastIndex) {
    //                getItem(items.lastIndex).order!! + 1
    //            } else {
    //                getItem(position).order!!
    //            }
    //
    //        items.shiftItemOrdersHigher(order, 1 + newItem.children.size)
    //        itemsChecked?.shiftItemOrdersHigher(order, 1 + newItem.children.size)
    //
    //        newItem.order = order
    //        newItem.children.forEachIndexed { index, child -> child.order = order + index + 1 }
    //        val forceIsChild =
    //            when {
    //                position == 0 -> false
    //                (position - 1).isBeforeChildItemOfOtherParent -> true
    //                newItem.isChild && items.findParent(newItem) == null -> true
    //                else -> null
    //            }
    //        if (forceIsChild == true) {
    //            newItem.isChild = true
    //            val actualPosition = position.coerceAtMost(items.lastIndex)
    //            items.updateChildInParent(actualPosition, newItem, clearChildren = false)
    //            items.addWithChildren(newItem)
    //        } else {
    //            newItem.isChild = forceIsChild ?: newItem.isChild
    //            items.addWithChildren(newItem)
    //        }
    //    }

    private fun setIdIfUnset(newItem: ListItem) {
        if (newItem.id == -1) {
            newItem.id = nextItemId++
        }
    }

    private fun isAutoSortByCheckedEnabled() =
        preferences.listItemSorting.value == ListItemSort.AUTO_SORT_BY_CHECKED

    private val Int.isChildItemOfOtherParent: Boolean
        get() {
            if (this < 0) {
                return false
            }
            val item = items[this]
            return item.isChild && !items[this + item.itemCount].isChildOf(this)
        }

    private val Int.isBeforeChildItem: Boolean
        get() {
            if (this < 0 || this > items.lastIndex - 1) {
                return false
            }
            return items[this + 1].isChild
        }

    private fun ListItem.isNextItemChild(position: Int): Boolean {
        return (position < items.size - itemCount) && (items[position + this.itemCount].isChild)
    }

    private fun ListItem.isChildOf(otherPosition: Int): Boolean {
        return isChildOf(items[otherPosition])
    }

    private fun ListItem.shouldParentBeUnchecked(): Boolean {
        return children.isNotEmpty() && !children.areAllChecked() && checked
    }

    private fun ListItem.shouldParentBeChecked(): Boolean {
        return children.isNotEmpty() && children.areAllChecked() && !checked
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

    fun findParent(item: ListItem) = items.findParent(item) ?: itemsChecked?.findParent(item)

    fun submitList(list: MutableList<ListItem>) {
        adapter.submitList(list)
    }

    companion object {
        private const val TAG = "ListManager"
    }
}

data class ListState(val items: MutableList<ListItem>, val checkedItems: MutableList<ListItem>?)
