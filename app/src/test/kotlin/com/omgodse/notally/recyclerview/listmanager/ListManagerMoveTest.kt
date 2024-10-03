package com.omgodse.notally.recyclerview.listmanager

import com.omgodse.notally.changehistory.ListMoveChange
import com.omgodse.notally.preferences.ListItemSorting
import com.omgodse.notally.recyclerview.lastIndex
import com.omgodse.notally.test.assert
import com.omgodse.notally.test.assertOrder
import com.omgodse.notally.test.createListItem
import com.omgodse.notally.test.printList
import com.omgodse.notally.test.simulateDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListManagerMoveTest : ListManagerTestBase() {
    // region move

    @Test
    fun `move parent without children`() {
        setSorting(ListItemSorting.noAutoSort)
        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B")
        assertEquals(1, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " D")
    }

    @Test
    fun `move parent with children into other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        val newPosition = listManager.move(3, 2)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertChildren()
        assertEquals(2, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 2, 2, " D(E)")
    }

    @Test
    fun `move parent with children to bottom`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        items.printList("Before")

        val newPosition = listManager.move(0, items.lastIndex)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
        assertEquals(items.lastIndex - 2, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(0, 5, 3, " A(B,C)")
    }

    @Test
    fun `move parent with children to top`() {
        setSorting(ListItemSorting.noAutoSort)
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
        (changeHistory.lookUp() as ListMoveChange).assert(4, 0, 0, " D(E)")
    }

    @Test
    fun `move child to other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        assertEquals(1, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " > D")
    }

    @Test
    fun `move child above other child`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)

        val newPosition = listManager.move(5, 3)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
        assertEquals(3, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(5, 3, 3, " > F")
    }

    @Test
    fun `move child to top`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.move(3, 0)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
        assertEquals(0, newPosition)
    }

    @Test
    fun `dont move parent into own children`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)

        val newPosition = listManager.move(2, 3)

        "C".assertChildren("D", "E")
        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    @Test
    fun `dont move parent under checked item if auto-sort enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeChecked(5, true)

        val newPosition = listManager.move(2, 5)

        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    // endregion

    // region revertMove

    @Test
    fun `revertMove parent without children`() {
        setSorting(ListItemSorting.noAutoSort)
        val newPosition = listManager.move(1, 4)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 1, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    @Test
    fun `revertMove parent with children into other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(2, true, false)
        val newPosition = listManager.move(1, 3)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 1, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertChildren("C")
        "D".assertChildren()
    }

    @Test
    fun `revertMove move parent with children to bottom`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        val newPosition = listManager.move(0, items.lastIndex)!!
        items.printList("After move 0 to ${items.lastIndex}")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 0, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B", "C")
        "F".assertChildren()
    }

    @Test
    fun `revertMove parent with children to top`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")
        val newPosition = listManager.move(3, 0)!!
        items.printList("After move 3 to 0")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B")
        "D".assertChildren("E")
    }

    @Test
    fun `revertMove child to other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)
        val newPosition = listManager.move(3, 1)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
        "A".assertChildren()
    }

    @Test
    fun `revertMove child above other child`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")
        val newPosition = listManager.move(5, 3)!!
        items.printList("After move 5 to 3")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 5, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D", "E", "F")
    }

    @Test
    fun `revertMove child to top`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)
        val newPosition = listManager.move(3, 0)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
    }

    // endregion

    // region endDrag

    @Test
    fun `endDrag parent without children`() {
        setSorting(ListItemSorting.noAutoSort)
        val newPosition = listManager.simulateDrag(3, 1)

        items.assertOrder("A", "D", "B")
        assertEquals(1, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " D")
    }

    @Test
    fun `endDrag parent with children into other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(3, 2)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertChildren()
        assertEquals(2, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 2, 2, " D(E)")
    }

    @Test
    fun `endDrag parent with children to bottom`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(0, items.lastIndex)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
        assertEquals(items.lastIndex - 2, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(0, 5, 3, " A(B,C)")
    }

    @Test
    fun `endDrag parent with children to top`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(3, 0)
        items.printList("After move 3 to 0")

        items.assertOrder("D", "E", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
        assertEquals(0, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 0, 0, " D(E)")
    }

    @Test
    fun `endDrag child to other parent`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.simulateDrag(3, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        assertEquals(1, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " > D")
    }

    @Test
    fun `endDrag child above other child`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)

        val newPosition = listManager.simulateDrag(5, 3)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
        assertEquals(3, newPosition)
        (changeHistory.lookUp() as ListMoveChange).assert(5, 3, 3, " > F")
    }

    @Test
    fun `endDrag child to top`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true, false)

        val newPosition = listManager.simulateDrag(3, 0)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
        assertEquals(0, newPosition)
    }

    // endregion

}