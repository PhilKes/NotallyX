package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.view.misc.ListItemSorting
import com.philkes.notallyx.test.assert
import com.philkes.notallyx.test.assertChecked
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.utils.changehistory.ChangeCheckedForAllChange
import com.philkes.notallyx.utils.changehistory.ListCheckedChange
import org.junit.Test

class ListManagerCheckedTest : ListManagerTestBase() {

    // region changeChecked

    @Test
    fun `changeChecked checks item`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertSortingPosition(0)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0)
    }

    @Test
    fun `changeChecked unchecks item and updates order`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeChecked(0, true)
        listManager.changeChecked(0, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "A".assertSortingPosition(0)
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 0)
    }

    @Test
    fun `changeChecked does nothing when state is unchanged`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeChecked(0, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertSortingPosition(0)
    }

    @Test
    fun `changeChecked on child item`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true)

        listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 1)
    }

    @Test
    fun `changeChecked on parent item checks all children`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "B".assertIsChecked()
        "C".assertIsChecked()
        "A".assertChildren("B", "C")
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        "C".assertSortingPosition(2)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0)
    }

    @Test
    fun `changeChecked on child item and does not move when auto-sort is enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)

        listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 1)
    }

    @Test
    fun `changeChecked true on parent item checks all children and moves when auto-sort is enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "B".assertIsChecked()
        "C".assertIsChecked()
        "A".assertChildren("B", "C")
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        "C".assertSortingPosition(2)
        "A".assertPosition(3)
        "B".assertPosition(4)
        "C".assertPosition(5)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0)
    }

    @Test
    fun `changeChecked false on parent item unchecks all children and moves when auto-sort is enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)

        listManager.changeChecked(3, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "A".assertChildren("B", "C")
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        "C".assertSortingPosition(2)
        "A".assertPosition(0)
        "B".assertPosition(1)
        "C".assertPosition(2)
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 0)
    }

    @Test
    fun `changeChecked false on parent item after add another item when auto-sort is enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)
        listManager.addWithChildren(0, "Parent1", "Child1", "Child2")

        listManager.changeChecked(6, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "A".assertChildren("B", "C")
        items.assertOrder("Parent1", "Child1", "Child2", "A", "B", "C")
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 0)
    }

    @Test
    fun `changeChecked false on child item also unchecks parent`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(1, checked = true, pushChange = true)

        listManager.changeChecked(4, checked = false, pushChange = true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "D".assertIsChecked()
        "B".assertChildren("C", "D")
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 2)
    }

    // endregion

    // region changeCheckedForAll

    @Test
    fun `changeCheckedForAll true checks all unchecked items`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(true, listOf(5, 4, 2, 0))
    }

    @Test
    fun `changeCheckedForAll false unchecks all unchecked items`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(false, listOf(3, 2, 1))
    }

    @Test
    fun `changeCheckedForAll true correct order with auto-sort enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(true, listOf(5, 4, 1))
    }

    @Test
    fun `changeCheckedForAll false correct order with auto-sort enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(false, listOf(3, 2, 0))
    }

    // endregion

}
