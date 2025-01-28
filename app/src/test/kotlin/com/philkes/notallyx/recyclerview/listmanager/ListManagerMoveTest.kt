package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.view.note.listitem.sorting.lastIndex
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.test.createListItem
import com.philkes.notallyx.test.printList
import com.philkes.notallyx.test.simulateDrag
import com.philkes.notallyx.utils.changehistory.ListMoveChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListManagerMoveTest : ListManagerTestBase() {

    // region move

    @Test
    fun `move parent without children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B")
        assertEquals(1, newPosition)
    }

    @Test
    fun `move parent with children into other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        val newPosition = listManager.move(3, 2)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertIsChild()
        assertEquals(2, newPosition)
    }

    @Test
    fun `move parent with children to bottom`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        items.printList("Before")

        val newPosition = listManager.move(0, items.lastIndex)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
        assertEquals(items.lastIndex - 2, newPosition)
    }

    @Test
    fun `move parent with children to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.add(0, createListItem("G"))
        items.printList("Before")

        val newPosition = listManager.move(4, 0)
        items.printList("After move 4 to 0")

        items.assertOrder("D", "E", "G", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
        assertEquals(0, newPosition)
    }

    @Test
    fun `move child to other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        assertEquals(1, newPosition)
    }

    @Test
    fun `move child above other child`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)

        val newPosition = listManager.move(5, 3)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
        assertEquals(3, newPosition)
    }

    @Test
    fun `move child to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.move(3, 0)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
        assertEquals(0, newPosition)
    }

    @Test
    fun `dont move parent into own children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)

        val newPosition = listManager.move(2, 3)

        "C".assertChildren("D", "E")
        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    @Test
    fun `dont move parent under checked item if auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeChecked(5, true)

        val newPosition = listManager.move(2, 5)

        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    // endregion

    // region undoMove

    @Test
    fun `setItems parent without children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.move(1, 4)!!
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    @Test
    fun `setItems parent with children into other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(2, true, false)
        listManager.move(1, 3)!!
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertChildren("C")
        "D".assertChildren()
    }

    @Test
    fun `setItems move parent with children to bottom`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.move(0, items.lastIndex)!!
        items.printList("After move 0 to ${items.lastIndex}")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B", "C")
        "F".assertChildren()
    }

    @Test
    fun `setItems parent with children to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")
        listManager.move(3, 0)!!
        items.printList("After move 3 to 0")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B")
        "D".assertChildren("E")
    }

    @Test
    fun `setItems child to other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.move(3, 1)!!
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
        "A".assertChildren()
    }

    @Test
    fun `setItems child above other child`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")
        listManager.move(5, 3)!!
        items.printList("After move 5 to 3")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D", "E", "F")
    }

    @Test
    fun `setItems child to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.move(3, 0)!!
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemsBefore

        listManager.setItems(itemsBeforeMove, false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
    }

    // endregion

    // region endDrag

    @Test
    fun `endDrag parent without children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listItemDragCallback.simulateDrag(3, 1, "D".itemCount)

        items.assertOrder("A", "D", "B")
    }

    @Test
    fun `endDrag parent with children into other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(4, 2, "E".itemCount)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "E", "F", "C", "D")
        "A".assertChildren("B", "E", "F", "C")
        "D".assertChildren()
    }

    @Test
    fun `endDrag parent with children into other parent with auto-sort enabled`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")

        // TODO: parents cant be moved into other parents' children yet
        listItemDragCallback.simulateDrag(4, 2, "E".itemCount)
        items.printList("After move 4 to 2")

        items.assertOrder("A", "B", "E", "F", "C", "D")
        "A".assertChildren("B", "E", "F", "C")
        "D".assertChildren()
    }

    @Test
    fun `endDrag parent with children to bottom`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(0, items.lastIndex, "A".itemCount)
        items.printList("After move 0 to ${items.lastIndex}")
        // TODO: test is faulty?
        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
    }

    @Test
    fun `endDrag parent with children to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(3, 0, "D".itemCount)
        items.printList("After move 3 to 0")

        items.assertOrder("D", "E", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
    }

    @Test
    fun `endDrag child to other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        listItemDragCallback.simulateDrag(3, 1, "D".itemCount)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
    }

    @Test
    fun `endDrag child above other child`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)

        listItemDragCallback.simulateDrag(5, 3, "F".itemCount)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
    }

    @Test
    fun `endDrag child to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        listItemDragCallback.simulateDrag(3, 0, "D".itemCount)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
    }

    // endregion

}
