package com.omgodse.notally.recyclerview

import android.text.TextWatcher
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.omgodse.notally.changehistory.ChangeCheckedForAllChange
import com.omgodse.notally.changehistory.ChangeHistory
import com.omgodse.notally.changehistory.DeleteCheckedChange
import com.omgodse.notally.changehistory.ListAddChange
import com.omgodse.notally.changehistory.ListCheckedChange
import com.omgodse.notally.changehistory.ListDeleteChange
import com.omgodse.notally.changehistory.ListEditTextChange
import com.omgodse.notally.changehistory.ListIsChildChange
import com.omgodse.notally.changehistory.ListMoveChange
import com.omgodse.notally.miscellaneous.CheckedSorter
import com.omgodse.notally.preferences.ListItemSorting
import com.omgodse.notally.preferences.Preferences
import com.omgodse.notally.recyclerview.viewholder.MakeListVH
import com.omgodse.notally.room.ListItem

/**
 * Should be used for all changes to the items list. Notifies the RecyclerView.Adapter and pushes
 * according changes to the ChangeHistory
 */
class ListManager(
    private val recyclerView: RecyclerView,
    private val changeHistory: ChangeHistory,
    private val preferences: Preferences,
    private val inputMethodManager: InputMethodManager,
) {

    private var nextItemId: Int = 0
    private lateinit var items: ListItemSortedList
    internal lateinit var adapter: RecyclerView.Adapter<MakeListVH>

    fun add(
        position: Int = items.size(),
        item: ListItem = defaultNewItem(position),
        pushChange: Boolean = true,
    ) {
        val itemBeforeInsert = item.clone() as ListItem
        items.beginBatchedUpdates()
        for ((idx, newItem) in (item + item.children).withIndex()) {
            val insertPosition = position + idx
            if (newItem.id == -1) {
                newItem.id = nextItemId++
            }
            items.addToSortingPositions(insertPosition until items.size(), 1)
            newItem.sortingPosition = insertPosition

            val forceIsChild =
                when {
                    insertPosition == 0 -> false
                    (insertPosition - 1).isBeforeChildItemOfOtherParent -> true
                    else -> null
                }
            items.add(newItem, forceIsChild)
        }
        items.endBatchedUpdates()
        //        sortAndUpdate()
        //        items.updateAllChildren()
        val positionAfterAdd = items.indexOf(item)
        if (pushChange) {
            changeHistory.push(ListAddChange(position, positionAfterAdd, itemBeforeInsert, this))
        }
        recyclerView.post {
            val viewHolder =
                recyclerView.findViewHolderForAdapterPosition(positionAfterAdd) as MakeListVH?
            if (!item.checked && viewHolder != null) {
                viewHolder.focusEditText(inputMethodManager = inputMethodManager)
            }
        }
    }

    /**
     * Deletes item and its children at given position.
     *
     * @param force if false, deletion can be rejected, e.g. if trying to delete the first item
     * @param childrenToDelete can be used when a ListAddChange is undone to pass the item at the
     *   state before the insertion
     * @param allowFocusChange if true the UI will focus the last valid ListItem's EditText
     * @return the removed ListItem or null if deletion was rejected
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
            changeHistory.push(ListDeleteChange(position, item, this))
        }
        return item
    }

    /** @return position of the moved item afterwards */
    fun move(
        positionFrom: Int,
        positionTo: Int,
        pushChange: Boolean = true,
        updateChildren: Boolean = true,
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
                positionTo == 0 -> false
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
        updateChildren: Boolean,
        pushChange: Boolean,
    ) {
        if (updateChildren) {
            if (newPosition.isBeforeChildItemOfOtherParent) {
                items.setIsChild(newPosition, true, true)
            } else if (newPosition == 0) {
                items.setIsChild(newPosition, false)
            } else {
                //                items.updateAllChildren()
            }
        }
        if (pushChange) {
            changeHistory.push(
                ListMoveChange(positionFrom, positionTo, newPosition, itemBeforeMove, this)
            )
        }
    }

    fun revertMove(positionAfter: Int, positionFrom: Int, itemBeforeMove: ListItem) {
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
        } else {
            //            items.updateAllChildren()
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

    fun changeChecked(position: Int, checked: Boolean, pushChange: Boolean = true): Int {
        val item = items[position]
        if (item.checked == checked) {
            return position
        }
        //        if (checked) {
        //            item.sortingPosition = position
        //        }
        if (item.isChild) {
            return changeCheckedForChild(checked, item, pushChange, position)
        }
        val positionAfter = items.setCheckedWithChildren(position, checked)
        if (pushChange) {
            changeHistory.push(ListCheckedChange(checked, position, positionAfter, this))
        }
        return positionAfter
    }

    private fun changeCheckedForChild(
        checked: Boolean,
        item: ListItem,
        pushChange: Boolean,
        position: Int,
    ): Int {
        var changePushedByParent = false
        if (!checked) {
            val (parentPosition, parent) = items.findParent(item)!!
            if (parent.checked) {
                val parentPositionAfter = items.setChecked(parentPosition, false)
                if (pushChange) {
                    changeHistory.push(
                        ListCheckedChange(false, parentPosition, parentPositionAfter, this)
                    )
                    changePushedByParent = true
                }
            }
        }
        val positionAfter = items.setChecked(position, checked)
        if (pushChange && !changePushedByParent) {
            changeHistory.push(ListCheckedChange(checked, position, positionAfter, this))
        }
        return positionAfter
    }

    fun changeCheckedForAll(checked: Boolean, pushChange: Boolean = true) {
        val (changedPositions, changedPositionsAfterSort) = check(checked, items.indices.toList())
        if (pushChange) {
            changeHistory.push(
                ChangeCheckedForAllChange(
                    checked,
                    changedPositions,
                    changedPositionsAfterSort,
                    this,
                )
            )
        }
    }

    //    fun sortAndUpdate() {
    //        items.sortAndUpdateItems(adapter = adapter)
    //    }

    fun check(checked: Boolean, positions: Collection<Int>): Pair<List<Int>, List<Int>> {
        return items.setChecked(positions, checked)
        //        val changedPositions = mutableListOf<Int>()
        //        items.beginBatchedUpdates()
        //        positions.forEach {
        //            val item = items[it]
        //            if (item.checked != checked) {
        //                changedPositions.add(it)
        ////                items.setCheckedAndNotify(it, checked)
        //                if (item.checked != checked) {
        //                    item.checked = checked
        ////                    this.updateItemAt(position, item)
        //                }
        //            }
        //        }
        //        val changedItems = changedPositions.map { items[it] }.toMutableList()
        //        items.endBatchedUpdates()
        //        val changedPositionsAfterSort = changedItems.map { items.indexOf(it)
        // }.toMutableList()
        //        return Pair(changedPositions, changedPositionsAfterSort)
    }

    fun changeIsChild(position: Int, isChild: Boolean, pushChange: Boolean = true) {
        items.setIsChild(position, isChild)
        if (pushChange) {
            changeHistory.push(ListIsChildChange(isChild, position, this))
        }
    }

    fun moveFocusToNext(position: Int) {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position + 1) as MakeListVH?
        if (viewHolder != null) {
            if (viewHolder.binding.CheckBox.isChecked) {
                moveFocusToNext(position + 1)
            } else viewHolder.binding.EditText.requestFocus()
        } else add(pushChange = false)
    }

    fun getItem(position: Int): ListItem {
        return items[position]
    }

    fun deleteCheckedItems(pushChange: Boolean = true) {
        val itemsBeforeDelete = items.toMutableList()
        updateList(items.filter { !it.checked }.toMutableList())
        if (pushChange) {
            changeHistory.push(DeleteCheckedChange(itemsBeforeDelete, this))
        }
    }

    fun updateList(newList: MutableList<ListItem>) {
        items.replaceAll(newList)
    }

    fun initList(items: ListItemSortedList) {
        this.items = items
        this.items.forEachIndexed { index, item -> item.id = index }
        nextItemId = this.items.size()
        //        this.items.sortAndUpdateItems(initSortingPositions = true, adapter = adapter)
        Log.d(TAG, "initList:\n${this.items.toReadableString()}")
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

    private fun isAutoSortByCheckedEnabled() =
        preferences.listItemSorting.value == ListItemSorting.autoSortByChecked

    private val Int.isBeforeChildItemOfOtherParent: Boolean
        get() {
            if (this < 0) {
                return false
            }
            val item = items[this]
            return item.isNextItemChild(this) && !items[this + item.itemCount].isChildOf(this)
        }

    private fun ListItem.isNextItemChild(position: Int): Boolean {
        return (position < items.size() - itemCount) && (items[position + this.itemCount].isChild)
    }

    private fun ListItem.isChildOf(otherPosition: Int): Boolean {
        return isChildOf(items[otherPosition])
    }

    //    private fun MutableList<ListItem>.sortAndUpdateItems(
    //        newList: MutableList<ListItem> = items,
    //        initSortingPositions: Boolean = false,
    //        adapter: RecyclerView.Adapter<*>,
    //    ) {
    //        val sortedList =
    //            SORTERS[preferences.listItemSorting.value]?.sort(newList, initSortingPositions)
    //        this.updateList(sortedList ?: newList.toMutableList(), adapter)
    //    }

    companion object {
        private val SORTERS = mapOf(ListItemSorting.autoSortByChecked to CheckedSorter())
        private const val TAG = "ListManager"
    }
}
