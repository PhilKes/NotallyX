package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertChecked
import com.philkes.notallyx.test.assertIds
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.test.assertSize
import com.philkes.notallyx.test.simulateDrag
import org.junit.Test

class ListManagerWithChangeHistoryTest : ListManagerTestBase() {

    @Test
    fun `undo and redo moves`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listItemDragCallback.simulateDrag(0, 4, 1)
        listItemDragCallback.simulateDrag(2, 3, 1)
        listItemDragCallback.simulateDrag(4, 1, 1)
        listItemDragCallback.simulateDrag(0, 5, 1)
        listItemDragCallback.simulateDrag(5, 0, 1)
        listItemDragCallback.simulateDrag(3, 4, 1)
        listItemDragCallback.simulateDrag(1, 5, 1)
        val bodiesAfterMove = items.map { it.body }.toTypedArray()

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertOrder("A", "B", "C", "D", "E", "F")
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertOrder(*bodiesAfterMove)
    }

    @Test
    fun `undo and redo changeChecked`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeChecked(0, true)
        listManager.changeChecked(3, true)
        listManager.changeChecked(0, false)
        listManager.changeChecked(4, true)
        listManager.changeChecked(1, false)
        listManager.changeChecked(2, true)
        val checkedValues = items.map { it.checked }.toBooleanArray()

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertChecked(false, false, false, false, false, false)
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertChecked(*checkedValues)
    }

    @Test
    fun `undo and redo changeChecked if auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeChecked(0, true)
        listManager.changeChecked(3, true)
        listManager.changeChecked(0, false, inCheckedList = true)
        listManager.changeChecked(4, true)
        listManager.changeChecked(0, false, inCheckedList = true)
        listManager.changeChecked(2, true)
        val bodiesAfterMove = items.map { it.body }.toTypedArray()
        val checkedValues = items.map { it.checked }.toBooleanArray()

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertChecked(false, false, false, false, false, false)
        items.assertOrder("A", "B", "C", "D", "E", "F")
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertChecked(*checkedValues)
        items.assertOrder(*bodiesAfterMove)
    }

    @Test
    fun `undo and redo changeChecked false on child item`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(1, checked = true, pushChange = true)
        listManager.changeChecked(2, checked = false, inCheckedList = true, pushChange = true)

        changeHistory.undo()

        items.assertOrder("A", "E", "F")
        itemsChecked!!.assertOrder("B", "C", "D")
        "B".assertIsChecked()
        "C".assertIsChecked()
        "D".assertIsChecked()
        "B".assertChildren("C", "D")
    }

    @Test
    fun `undo and redo changeIsChild`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(4, true)
        listManager.changeIsChild(1, false)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, false)
        listManager.changeIsChild(4, true)
        // Afterwards: B has children C,D,E

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        listOf("A", "B", "C", "D", "E", "F").forEach { it.assertChildren() }
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        "A".assertChildren()
        "B".assertChildren("C", "D", "E")
        "F".assertChildren()
    }

    @Test
    fun `undo and redo add parents with children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.addWithChildren(0, "Parent1", "Child1")
        listManager.addWithChildren(4, "Parent2")
        listManager.addWithChildren(0, "Parent3")
        listManager.addWithChildren(3, "Parent4", "Child2")
        listManager.addWithChildren(parentBody = "Parent5")
        listManager.addWithChildren(items.lastIndex, "Parent6", "Child3", "Child4")
        val bodiesAfterAdd = items.map { it.body }.toTypedArray()

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertOrder("A", "B", "C", "D", "E", "F")
        listOf("A", "B", "C", "D", "E", "F").forEach { it.assertChildren() }
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertOrder(*bodiesAfterAdd)
        "Parent1".assertChildren("Child1")
        "Parent2".assertChildren()
        "Parent3".assertChildren()
        "Parent4".assertChildren("Child2")
        "Parent5".assertChildren()
        "Parent6".assertChildren("Child3", "Child4")
        items.assertIds(9, 6, 7, 10, 11, 0, 1, 8, 2, 3, 4, 5, 13, 14, 15, 12)
        listOf("A", "B", "C", "D", "E", "F").forEach { it.assertChildren() }
    }

    @Test
    fun `undo and redo delete parents with children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)
        changeHistory.reset()
        listManager.delete(0)
        listManager.delete(items.lastIndex)
        listManager.delete(0)
        items.assertSize(0)

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B")
        "C".assertChildren("D", "E")
        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertSize(0)
    }

    @Test
    fun `undo and redo check all with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        listManager.changeCheckedForAll(true)

        changeHistory.undo()
        items.assertOrder("B", "E", "F")
        items.assertChecked(false, false, false)
        itemsChecked!!.assertOrder("A", "C", "D")
        itemsChecked!!.assertChecked(true, true, true)

        changeHistory.redo()
        items.assertSize(0)
        itemsChecked!!.assertOrder("A", "B", "C", "D", "E", "F")
        itemsChecked!!.assertChecked(true, true, true, true, true, true)
    }

    @Test
    fun `undo and redo uncheck all with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        listManager.changeCheckedForAll(false)

        changeHistory.undo()
        items.assertOrder("B", "E", "F")
        items.assertChecked(false, false, false)
        itemsChecked!!.assertOrder("A", "C", "D")
        itemsChecked!!.assertChecked(true, true, true)

        changeHistory.redo()
        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
        itemsChecked!!.assertSize(0)
    }

    @Test
    fun `undo and redo delete checked with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        listManager.deleteCheckedItems()

        changeHistory.undo()
        items.assertOrder("B", "E", "F")
        itemsChecked!!.assertOrder("A", "C", "D")
        items.assertChecked(false, false, false)
        itemsChecked!!.assertChecked(true, true, true)

        changeHistory.redo()
        items.assertOrder("B", "E", "F")
        items.assertChecked(false, false, false)
        itemsChecked!!.assertSize(0)
    }

    @Test
    fun `undo and redo various changes with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)
        listManager.changeChecked(0, true)
        listManager.changeChecked(3, true)
        listManager.changeChecked(0, false, inCheckedList = true)
        listManager.delete(0, true)
        listManager.addWithChildren(0, "Parent", "Child1")
        listManager.delete(4)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(1, false)
        listManager.addWithChildren(3, "Parent4", "Child2", "Child3")
        listManager.changeCheckedForAll(true)
        //        listManager.deleteCheckedItems()
        //        changeHistory.undo()
        listManager.changeChecked(4, false, inCheckedList = true)
        listManager.delete(0)
        listManager.addWithChildren(0, "Parent6", "Child4")
        //        listManager.changeCheckedForAll(false)
        //        listManager.deleteCheckedItems()
        val uncheckedBodiesAfterAdd = items.filter { !it.checked }.map { it.body }.toTypedArray()
        val checkedBodiesAfterAdd = items.filter { it.checked }.map { it.body }.toTypedArray()
        items.assertOrder(*uncheckedBodiesAfterAdd)
        itemsChecked!!.assertOrder(*checkedBodiesAfterAdd)
        //        "Parent6".assertChildren("Child4")
        //        "Parent".assertChildren()

        while (changeHistory.canUndo.value) {
            changeHistory.undo()
        }
        items.assertOrder("A", "B", "C", "D", "E", "F")
        listOf("A", "B", "C", "D", "E", "F").forEach { it.assertChildren() }
        items.assertChecked(false, false, false, false, false, false)

        while (changeHistory.canRedo.value) {
            changeHistory.redo()
        }
        items.assertOrder(*uncheckedBodiesAfterAdd)
        itemsChecked!!.assertOrder(*checkedBodiesAfterAdd)
        "Parent6".assertChildren("Child4")
        //        "Parent".assertChildren()
    }
}
