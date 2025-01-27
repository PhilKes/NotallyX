package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.view.note.listitem.sorting.lastIndex
import com.philkes.notallyx.presentation.view.note.listitem.sorting.map
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertChecked
import com.philkes.notallyx.test.assertIds
import com.philkes.notallyx.test.assertOrder
import org.junit.Test

class ListManagerWithChangeHistoryTest : ListManagerTestBase() {

    @Test
    fun `undo and redo moves`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.move(0, 4)
        listManager.move(2, 3)
        listManager.move(4, 1)
        listManager.move(0, 5)
        listManager.move(5, 0)
        listManager.move(3, 4)
        listManager.move(1, 5)
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
        listManager.changeChecked(0, false)
        listManager.changeChecked(4, true)
        listManager.changeChecked(1, false)
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
        listManager.changeChecked(4, checked = false, pushChange = true)

        changeHistory.undo()

        items.assertOrder("A", "E", "F", "B", "C", "D")
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
        listManager.delete(0, true)
        listManager.delete(items.lastIndex, true)
        listManager.delete(0, true)
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
        items.assertOrder("B", "E", "F", "A", "C", "D")
        items.assertChecked(false, false, false, true, true, true)

        changeHistory.redo()
        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
    }

    @Test
    fun `undo and redo uncheck all with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        listManager.changeCheckedForAll(false)

        changeHistory.undo()
        items.assertOrder("B", "E", "F", "A", "C", "D")
        items.assertChecked(false, false, false, true, true, true)

        changeHistory.redo()
        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
    }

    @Test
    fun `undo and redo delete checked with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        listManager.deleteCheckedItems()

        changeHistory.undo()
        items.assertOrder("B", "E", "F", "A", "C", "D")
        items.assertChecked(false, false, false, true, true, true)

        changeHistory.redo()
        items.assertOrder("B", "E", "F")
        items.assertChecked(false, false, false)
    }

    //    @Test
    //    fun `undo and redo various changes with auto-sort enabled`() {
    //        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
    //        listManager.changeIsChild(1, true)
    //        listManager.changeIsChild(3, true)
    //        listManager.changeIsChild(4, true)
    //        listManager.changeChecked(0, true)
    //        listManager.changeChecked(3, true)
    //        listManager.changeChecked(0, false)
    //        listManager.delete(0, true)
    //        listManager.addWithChildren(0, "Parent", "Child1")
    //        listManager.delete(4, true)
    //        listManager.changeIsChild(2, true)
    //        listManager.changeIsChild(1, false)
    //        listManager.addWithChildren(3, "Parent4", "Child2", "Child3")
    //        listManager.changeCheckedForAll(true)
    //        //        listManager.deleteCheckedItems()
    //        //        changeHistory.undo()
    //        listManager.changeChecked(4, false)
    //        listManager.delete(0, true)
    //        listManager.addWithChildren(1, "Parent6", "Child4")
    //        //        listManager.changeCheckedForAll(false)
    //        //        listManager.deleteCheckedItems()
    //        val bodiesAfterAdd = items.map { it.body }.toTypedArray()
    //        val checkedValues = items.map { it.checked }.toBooleanArray()
    //        items.assertOrder(*bodiesAfterAdd)
    //        items.assertChecked(*checkedValues)
    //        "Parent6".assertChildren("Child4")
    //        "Parent".assertChildren()
    //
    //        while (changeHistory.canUndo.value) {
    //            changeHistory.undo()
    //        }
    //        items.assertOrder("A", "B", "C", "D", "E", "F")
    //        listOf("A", "B", "C", "D", "E", "F").forEach { it.assertChildren() }
    //        items.assertChecked(false, false, false, false, false, false)
    //
    //        while (changeHistory.canRedo.value) {
    //            changeHistory.redo()
    //        }
    //        items.assertOrder(*bodiesAfterAdd)
    //        items.assertChecked(*checkedValues)
    //        "Parent6".assertChildren("Child4")
    //        "Parent".assertChildren()
    //    }
}
