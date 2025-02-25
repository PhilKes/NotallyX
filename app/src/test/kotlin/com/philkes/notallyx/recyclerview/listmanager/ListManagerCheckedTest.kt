package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.data.model.printList
import com.philkes.notallyx.presentation.view.note.listitem.ListState
import com.philkes.notallyx.presentation.view.note.listitem.sorting.cloneList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertChecked
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.test.assertOrderValues
import com.philkes.notallyx.test.assertSize
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

        items.assertSize(3)
        itemsChecked!!.assertSize(3)
        itemsChecked!!.assertOrder("A", "B", "C")
        itemsChecked!!.assertChecked(true, true, true)
        "A".assertChildren("B", "C")
        items.assertOrder("D", "E", "F")
        items.assertChecked(false, false, false)
    }

    @Test
    fun `changeChecked false on parent item unchecks all children and moves when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)

        listManager.changeChecked(0, checked = false, isFromCheckedList = true, pushChange = true)

        items.assertSize(6)
        itemsChecked!!.assertSize(0)
        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
    }

    @Test
    fun `changeChecked false on parent item after add another item when auto-sort is enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)
        listManager.addWithChildren(0, "Parent1", "Child1", "Child2")

        listManager.changeChecked(0, checked = false, isFromCheckedList = true, pushChange = true)

        itemsChecked!!.assertSize(0)
        "A".assertIsNotChecked()
        "B".assertIsNotChecked()
        "C".assertIsNotChecked()
        "A".assertChildren("B", "C")
        items.assertOrder("Parent1", "Child1", "Child2", "A", "B", "C")
    }

    @Test
    fun `changeChecked false on child item also unchecks parent`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(1, checked = true, pushChange = true)

        listManager.changeChecked(1, checked = false, isFromCheckedList = true, pushChange = true)

        itemsChecked!!.assertSize(0)
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
        listManager.changeChecked(1, checked = false, isFromCheckedList = true, pushChange = true)
        listItemDragCallback.simulateDrag(3, 1, 2)
        items.printList("Before")
        listManager.changeChecked(1, checked = true, pushChange = true)
        items.printList("After")

        items.assertOrder("A", "B", "C", "F")
        itemsChecked!!.assertOrder("D", "E")
        "D".assertChildren("E")
        "D".assertIsChecked()
        "E".assertIsChecked()
    }

    @Test
    fun `changeChecked false with auto-sort correct orders`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, checked = true)
        listItemDragCallback.simulateDrag(1, 2, 1)
        items.printList("Before")
        listManager.changeChecked(0, checked = false, isFromCheckedList = true)
        items.printList("After")

        items.assertOrder("A", "C", "D", "E", "B", "F")
        items.assertOrderValues(0, 1, 2, 3, 4)
    }

    @Test
    fun `changeChecked true with auto-sort with other item having same body`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        val items = this.items.cloneList()
        items[1].body = "A"
        listManager.setItems(ListState(items, itemsChecked!!.toMutableList().cloneList()))

        listManager.changeChecked(1, checked = true)

        this.items.assertSize(5)
        this.items.assertOrder("A", "C", "D", "E", "F")
        itemsChecked!!.assertSize(1)
        itemsChecked!!.assertOrder("A")
    }

    @Test
    fun `changeChecked true with auto-sort parent with child with checked item in between`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeChecked(2, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(1, true)

        items.assertOrder("A", "E", "F")
        itemsChecked!!.assertOrder("B", "D", "C")
        "B".assertChildren("D")
    }

    @Test
    fun `changeChecked true with auto-sort parent with children with checked item in between`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeChecked(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)
        listManager.changeIsChild(2, true)

        listManager.changeChecked(1, true)

        items.assertOrder("A")
        itemsChecked!!.assertOrder("B", "D", "E", "F", "C")
        "B".assertChildren("D", "E", "F")
        "D".assertOrder(3)
        "E".assertOrder(4)
        "F".assertOrder(5)
        "C".assertOrder(2)
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

        items.assertSize(0)
        itemsChecked!!.assertOrder("A", "B", "C", "D", "E", "F")
        itemsChecked!!.assertChecked(true, true, true, true, true, true)
    }

    @Test
    fun `changeCheckedForAll false correct order with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)

        listManager.changeCheckedForAll(false)

        itemsChecked!!.assertSize(0)
        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
    }

    // endregion

}
