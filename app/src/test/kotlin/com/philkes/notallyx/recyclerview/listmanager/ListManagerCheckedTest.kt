package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertChecked
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.test.printList
import com.philkes.notallyx.test.simulateDrag
import org.junit.Test

class ListManagerCheckedTest : ListManagerTestBase() {

    // region changeChecked

    @Test
    fun `changeChecked checks item`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertOrder(0)
    }

    @Test
    fun `changeChecked unchecks item and updates order`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeChecked(0, true)
        listManager.changeChecked(0, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "A".assertOrder(0)
    }

    @Test
    fun `changeChecked does nothing when state is unchanged`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeChecked(0, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertOrder(0)
    }

    @Test
    fun `changeChecked on child item`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)

        listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "A".assertOrder(0)
        "B".assertOrder(1)
    }

    @Test
    fun `changeChecked on parent item checks all children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "B".assertIsChecked()
        "C".assertIsChecked()
        "A".assertChildren("B", "C")
        "A".assertOrder(0)
        "B".assertOrder(1)
        "C".assertOrder(2)
    }

    @Test
    fun `changeChecked on child item and does not move when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)

        listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "A".assertOrder(0)
        "B".assertOrder(1)
    }

    @Test
    fun `changeChecked true on parent item checks all children and moves when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "B".assertIsChecked()
        "C".assertIsChecked()
        "A".assertChildren("B", "C")
        "A".assertOrder(0)
        "B".assertOrder(1)
        "C".assertOrder(2)
        "A".assertPosition(3)
        "B".assertPosition(4)
        "C".assertPosition(5)
    }

    @Test
    fun `changeChecked false on parent item unchecks all children and moves when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)

        listManager.changeChecked(3, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "A".assertChildren("B", "C")
        "A".assertOrder(0)
        "B".assertOrder(1)
        "C".assertOrder(2)
        "A".assertPosition(0)
        "B".assertPosition(1)
        "C".assertPosition(2)
    }

    @Test
    fun `changeChecked false on parent item after add another item when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)
        listManager.addWithChildren(0, "Parent1", "Child1", "Child2")

        listManager.changeChecked(6, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "A".assertChildren("B", "C")
        items.assertOrder("A", "B", "C", "Parent1", "Child1", "Child2")
    }

    @Test
    fun `changeChecked false on child item also unchecks parent`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(1, checked = true, pushChange = true)

        listManager.changeChecked(4, checked = false, pushChange = true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "D".assertIsChecked()
        "B".assertChildren("C", "D")
    }

    @Test
    fun `changeChecked after parent with child moved`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(4, true)
        listManager.changeChecked(3, checked = true, pushChange = true)
        listManager.changeChecked(4, checked = false, pushChange = true)
        listItemDragCallback.simulateDrag(3, 1, 2)
        items.printList("Before")
        listManager.changeChecked(1, checked = true, pushChange = true)
        items.printList("After")

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "D".assertChildren("E")
        "D".assertIsChecked()
        "E".assertIsChecked()
    }

    // endregion

    // region changeCheckedForAll

    @Test
    fun `changeCheckedForAll true checks all unchecked items`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
    }

    @Test
    fun `changeCheckedForAll false unchecks all unchecked items`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
    }

    @Test
    fun `changeCheckedForAll true correct order with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
    }

    @Test
    fun `changeCheckedForAll false correct order with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
    }

    // endregion

}
