package com.philkes.notallyx.presentation.view.note.listitem

import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.check
import com.philkes.notallyx.data.model.findChild
import com.philkes.notallyx.data.model.plus
import com.philkes.notallyx.data.model.shouldParentBeChecked
import com.philkes.notallyx.data.model.shouldParentBeUnchecked
import com.philkes.notallyx.presentation.view.note.listitem.adapter.CheckedListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.sorting.SortedItemsList
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.autoSortByCheckedEnabled
import com.philkes.notallyx.utils.changehistory.ChangeCheckedForAllChange
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.changehistory.DeleteCheckedChange
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.changehistory.ListAddChange
import com.philkes.notallyx.utils.changehistory.ListBatchChange
import com.philkes.notallyx.utils.changehistory.ListCheckedChange
import com.philkes.notallyx.utils.changehistory.ListDeleteChange
import com.philkes.notallyx.utils.changehistory.ListEditTextChange
import com.philkes.notallyx.utils.changehistory.ListIsChildChange
import com.philkes.notallyx.utils.changehistory.ListMoveChange
import com.philkes.notallyx.utils.lastIndex

data class ListState(
    val items: MutableList<ListItem>,
    val checkedItems: MutableList<ListItem>?,
    val focusedItemPos: Int? = null,
    val cursorPos: Int? = null,
)

/**
 * Should be used for all changes to the items list. Notifies the [RecyclerView.Adapter] and pushes
 * according changes to the [ChangeHistory]
 */
class ListManager(
    private val recyclerView: RecyclerView,
    internal val changeHistory: ChangeHistory,
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

    private var itemsChecked: SortedItemsList? = null
    private var batchChangeBeforeState: ListState? = null

    fun init(
        adapter: ListItemAdapter,
        itemsChecked: SortedItemsList? = null,
        adapterChecked: CheckedListItemAdapter? = null,
    ) {
        this.adapter = adapter
        this.itemsChecked = itemsChecked
        this.checkedAdapter = adapterChecked
        nextItemId = this.items.size + (this.itemsChecked?.size() ?: 0)
        Log.d(TAG, "initList:\n${this.items.toReadableString()}")
        this.itemsChecked?.let { Log.d(TAG, "itemsChecked:\n${it}") }
    }

    internal fun getState(selectedPos: Int? = null): ListState {
        val (pos, cursorPos) = recyclerView.getFocusedPositionAndCursor()
        return ListState(
            items.cloneList(),
            itemsChecked?.toMutableList()?.cloneList(),
            selectedPos ?: pos,
            cursorPos,
        )
    }

    internal fun setState(state: ListState) {
        adapter.submitList(state.items) {
            state.focusedItemPos?.let { itemPos -> focusItem(itemPos, state.cursorPos) }
        }
        this.itemsChecked?.setItems(state.checkedItems!!)
    }

    private fun focusItem(itemPos: Int, cursorPos: Int?) {
        // Focus item's EditText and set cursor position
        recyclerView.post {
            if (itemPos in 0..items.size) {
                recyclerView.smoothScrollToPosition(itemPos)
                (recyclerView.findViewHolderForAdapterPosition(itemPos) as? ListItemVH?)?.let {
                    viewHolder ->
                    inputMethodManager?.let { inputManager ->
                        val maxCursorPos = viewHolder.binding.EditText.length()
                        viewHolder.focusEditText(
                            selectionStart = cursorPos?.coerceIn(0, maxCursorPos) ?: maxCursorPos,
                            inputMethodManager = inputManager,
                        )
                    }
                }
            }
        }
    }

    fun add(
        position: Int = items.size,
        item: ListItem = defaultNewItem(position.coerceAtMost(items.size)),
        pushChange: Boolean = true,
    ) {
        val stateBefore = getState()
        (item + item.children).forEach { setIdIfUnset(it) }

        val insertOrder =
            if (position < 1) {
                0
            } else if (position <= items.lastIndex) {
                items[position - 1].order!! + 1
            } else {
                items.lastOrNull()?.let { it.order!! + 1 } ?: 0
            }
        shiftItemOrdersHigher(insertOrder - 1, 1 + item.children.size)
        item.order = insertOrder
        item.children.forEachIndexed { index, child -> child.order = insertOrder + 1 + index }

        val parentPos =
            if (position <= items.lastIndex && items[position].isChild) {
                findParent(items[position])?.first
            } else null

        val (insertPos, count) = items.addWithChildren(item)
        if (item.isChild) {
            items.addToParent(insertPos)
        } else if (parentPos != null) {
            val childrenBelow = items.removeChildrenBelowPositionFromParent(parentPos, insertPos)
            item.children.addAll(childrenBelow)
        }
        adapter.notifyItemRangeInserted(insertPos, count)
        items.notifyPreviousFirstItem(insertPos, count)
        if (pushChange) {
            changeHistory.push(ListAddChange(stateBefore, getState(selectedPos = insertPos), this))
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
        inCheckedList: Boolean = false,
        force: Boolean = true,
        pushChange: Boolean = true,
        allowFocusChange: Boolean = true,
    ): Boolean {
        // TODO
        //        endSearch?.invoke()
        val stateBefore = getState()
        val items = this.items.toMutableList()
        var result = false
        if (position.isValidPosition(forCheckedList = inCheckedList)) {
            return false
        }
        if (force || position > 0) {
            val item = getItem(position, inCheckedList)
            shiftItemOrdersHigher(item.order!! - 1, 1 + item.children.size, items = items)
            if (inCheckedList) {
                itemsChecked!!.removeFromParent(item)
                itemsChecked!!.removeWithChildren(item)
            } else {
                val parent = items.removeFromParent(item)
                parent?.updateParentChecked(items)
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

    /** @return position of the moved item afterwards and the moved item count. */
    fun move(positionFrom: Int, positionTo: Int): Pair<Int, Int> {
        val itemsCheckedBefore = itemsChecked?.toMutableList()?.cloneList()
        val list = items.toMutableList()
        val movedItem = list[positionFrom]
        // Do not allow to move parent into its own children
        if (
            !movedItem.isChild &&
                positionTo in (positionFrom..positionFrom + movedItem.children.size)
        ) {
            return Pair(-1, -1)
        }

        val itemCount = 1 + movedItem.children.size
        val isMoveUpwards = positionFrom < positionTo

        val fromOrder = list[positionFrom].order!!
        val toOrder = list[positionTo].order!!
        val insertOrder = if (isMoveUpwards) toOrder - itemCount + 1 else toOrder
        val (orderRange, valueToAdd) =
            if (isMoveUpwards) {
                Pair(fromOrder + itemCount until toOrder + 1, -itemCount)
            } else {
                Pair(toOrder until fromOrder, itemCount)
            }
        shiftItemOrders(orderRange, valueToAdd, items = list)
        itemsCheckedBefore?.shiftItemOrders(orderRange, valueToAdd)

        list.removeFromParent(movedItem)
        list.removeWithChildren(movedItem)

        (movedItem + movedItem.children).forEachIndexed { index, item ->
            item.order = insertOrder + index
        }
        val (insertIdx, count) = list.addWithChildren(movedItem)
        adapter.submitList(list)
        return Pair(insertIdx, count)
    }

    /** Finishes a drag movement by updating [ListItem.isChild] accordingly. */
    fun finishMove(
        positionTo: Int,
        count: Int,
        parentBefore: ListItem?,
        stateBefore: ListState,
        pushChange: Boolean,
    ) {
        val item = items[positionTo]
        val itemBelow = items.getOrNull(positionTo + count)
        val forceIsChild = itemBelow?.isChild == true && !item.isChild
        val positionFrom = stateBefore.items.indexOfFirst { it.id == item.id }
        var isChildChanged = false
        if (positionTo == 0) {
            item.isChild = false
            items.notifyPreviousFirstItem(0, count)
            isChildChanged = true
        } else if (forceIsChild) {
            item.isChild = true
            isChildChanged = true
        }
        if (positionFrom == 0) {
            adapter.notifyItemChanged(0)
            isChildChanged = true
        }

        if (item.isChild) {
            items.refreshParent(positionTo)?.updateParentChecked()
        }
        parentBefore?.updateParentChecked()
        if (isChildChanged) {
            adapter.notifyItemChanged(positionTo)
        }
        if (pushChange) {
            changeHistory.push(ListMoveChange(stateBefore, getState(), this))
        }
    }

    fun changeText(position: Int, value: EditTextState, pushChange: Boolean = true) {
        val stateBefore = getState()
        //        if(!pushChange) {
        endSearch?.invoke()
        //        }
        val item = items[position]
        item.body = value.getEditableText().toString()
        if (pushChange) {
            changeHistory.push(ListEditTextChange(stateBefore, getState(), this))
            // TODO: fix focus change
            // refreshSearch?.invoke(editText)
        }
    }

    fun changeChecked(
        position: Int,
        checked: Boolean,
        inCheckedList: Boolean = false,
        pushChange: Boolean = true,
    ) {
        val beforeState = getState()
        val item = getItem(position, inCheckedList)
        if (item.checked == checked) {
            return
        }
        if (item.isChild) {
            changeCheckedChild(position, item, checked, inCheckedList)
        } else {
            changeCheckedParent(item, checked, changeChildren = true)
        }
        if (pushChange) {
            changeHistory.push(ListCheckedChange(beforeState, getState(), this))
        }
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
    }

    fun changeIsChild(position: Int, isChild: Boolean, pushChange: Boolean = true) {
        val stateBefore = getState()
        items.findParentPosition(position)?.let { parentPos ->
            val nearestParent = items[parentPos]
            val item = items[position]
            item.isChild = isChild
            if (isChild) {
                items.refreshParent(position)
            } else {
                nearestParent.children.apply {
                    val childIndex = indexOf(item)
                    val childrenBelow = filterIndexed { idx, _ -> idx > childIndex }
                    removeAll(childrenBelow)
                    remove(item)
                    item.children = childrenBelow.toMutableList()
                }
            }
            item.updateParentChecked()
            nearestParent.updateParentChecked()
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
            } else viewHolder.binding.EditText.apply { focusAndSelect(getTextClone().length, -1) }
        } else add(pushChange = false)
    }

    fun deleteCheckedItems(pushChange: Boolean = true) {
        endSearch?.invoke()
        val stateBefore = getState()
        val itemsUpdated = items.cloneList()
        itemsUpdated.deleteCheckedItems()
        adapter.submitList(itemsUpdated)
        itemsChecked?.deleteCheckedItems()
        if (pushChange) {
            changeHistory.push(DeleteCheckedChange(stateBefore, getState(), this))
        }
    }

    fun findParent(item: ListItem) = items.findParent(item) ?: itemsChecked?.findParent(item)

    internal fun startBatchChange(cursorPos: Int? = null) {
        batchChangeBeforeState = getState()
        cursorPos?.let { batchChangeBeforeState = batchChangeBeforeState!!.copy(cursorPos = it) }
    }

    internal fun finishBatchChange(focusedItemPos: Int? = null) {
        batchChangeBeforeState?.let {
            val state =
                focusedItemPos?.let {
                    getState().copy(focusedItemPos = focusedItemPos, cursorPos = null)
                } ?: getState()
            changeHistory.push(ListBatchChange(it, state, this))
        }
    }

    internal fun getItem(position: Int, fromCheckedList: Boolean = false): ListItem {
        return if (fromCheckedList) itemsChecked!![position] else items[position]
    }

    private fun RecyclerView.getFocusedPositionAndCursor(): Pair<Int?, Int?> {
        return focusedChild?.let { view ->
            val position = getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) {
                return Pair(null, null)
            }
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
            val cursorPos = (viewHolder as? ListItemVH)?.binding?.EditText?.selectionStart
            return Pair(position, cursorPos)
        } ?: Pair(null, null)
    }

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

    private fun changeCheckedParent(
        parent: ListItem,
        checked: Boolean,
        changeChildren: Boolean,
        items: MutableList<ListItem> = this@ListManager.items,
    ) {
        if (checked) {
            // A parent from unchecked is checked
            if (preferences.autoSortByCheckedEnabled) {
                checkWithAutoSort(parent, items)
            } else {
                parent.check(true, checkChildren = changeChildren)
                if (items == this@ListManager.items) {
                    adapter.notifyListItemChanged(parent.id)
                }
            }
        } else {
            if (preferences.autoSortByCheckedEnabled) {
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
        inCheckedList: Boolean,
    ) {
        if (checked) {
            child.checked = true
            adapter.notifyItemChanged(position)
            val (_, parent) = items.findParent(child)!!
            parent.updateParentChecked()
        } else {
            if (inCheckedList) {
                uncheckWithAutoSort(child)
            } else {
                child.checked = false
                adapter.notifyItemChanged(position)
                checkParent(child, false)
            }
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

    private fun ListItem.updateParentChecked(
        items: MutableList<ListItem> = this@ListManager.items
    ) {
        if (isChild) {
            return
        }
        if (shouldParentBeChecked()) {
            changeCheckedParent(this, true, changeChildren = true, items = items)
        }
        if (shouldParentBeUnchecked()) {
            changeCheckedParent(this, false, changeChildren = false, items = items)
        }
    }

    private fun checkParent(item: ListItem, checked: Boolean) {
        val (parentPos, parent) = items.findParent(item)!!
        if (parent.checked != checked) {
            parent.checked = checked
            adapter.notifyItemChanged(parentPos)
        }
    }

    private fun setIdIfUnset(newItem: ListItem) {
        if (newItem.id == -1) {
            newItem.id = nextItemId++
        }
    }

    /** Adds [valueToAdd] to all [ListItem.order] that are higher than [threshold] */
    private fun shiftItemOrdersHigher(
        threshold: Int,
        valueToAdd: Int,
        items: List<ListItem> = this.items,
    ) {
        items.shiftItemOrdersHigher(threshold, valueToAdd)
        itemsChecked?.shiftItemOrdersHigher(threshold, valueToAdd)
    }

    /** Adds [valueToAdd] to all [ListItem.order] that are in [orderRange] */
    private fun shiftItemOrders(
        orderRange: IntRange,
        valueToAdd: Int,
        items: List<ListItem> = this.items,
    ) {
        items.shiftItemOrders(orderRange, valueToAdd)
        itemsChecked?.shiftItemOrders(orderRange, valueToAdd)
    }

    private fun MutableList<ListItem>.notifyPreviousFirstItem(position: Int, count: Int) {
        if (position == 0 && size > count) {
            // To trigger enabling isChild swiping for the item that was previously at pos 0
            adapter.notifyItemChanged(count)
        }
    }

    private fun Int.isValidPosition(forCheckedList: Boolean = false): Boolean {
        return this < 0 ||
            this > (if (forCheckedList) itemsChecked!!.lastIndex else items.lastIndex)
    }

    companion object {
        private const val TAG = "ListManager"
    }
}
