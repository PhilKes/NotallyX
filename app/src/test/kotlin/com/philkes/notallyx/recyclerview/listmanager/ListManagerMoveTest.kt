package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.view.note.listitem.printList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.test.createListItem
import com.philkes.notallyx.test.simulateDrag
import com.philkes.notallyx.utils.changehistory.ListMoveChange
import org.junit.Test

class ListManagerMoveTest : ListManagerTestBase() {

    // region move

    @Test
    fun `move parent without children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listItemDragCallback.simulateDrag(3, 1, 1)

        items.assertOrder("A", "D", "B")
    }

    @Test
    fun `move parent with children into other parent above`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(3, 2, "D".itemCount)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertIsChild()
    }

    @Test
    fun `move parent with children into other parent below`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(0, 1, "A".itemCount)
        items.printList("After move 3 to 2")

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "A".assertIsChild()
        "D".assertChildren("A", "B", "C", "E")
    }

    @Test
    fun `move parent with children to bottom`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        items.printList("Before")

        val itemCount = "A".itemCount
        val positionTo = items.lastIndex - itemCount + 1
        listItemDragCallback.simulateDrag(0, positionTo, itemCount)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
    }

    @Test
    fun `move parent with children to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.add(0, createListItem("G"))
        items.printList("Before")

        listItemDragCallback.simulateDrag(4, 0, "D".itemCount)
        items.printList("After move 4 to 0")

        items.assertOrder("D", "E", "G", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
    }

    @Test
    fun `move child to other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        listItemDragCallback.simulateDrag(3, 1, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
    }

    @Test
    fun `move child above other child`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)

        listItemDragCallback.simulateDrag(5, 3, 1)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
    }

    @Test
    fun `move child to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)

        listItemDragCallback.simulateDrag(3, 0, 1)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
    }

    @Test
    fun `dont move parent into own children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)

        listItemDragCallback.simulateDrag(2, 3, 1)

        "C".assertChildren("D", "E")
        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    // endregion

    // region undoMove

    @Test
    fun `setItems parent without children`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listItemDragCallback.simulateDrag(1, 4, "B".itemCount)
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
    }

    @Test
    fun `setItems parent with children into other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(2, true, false)
        listItemDragCallback.simulateDrag(1, 3, "B".itemCount)
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertChildren("C")
        "D".assertChildren()
    }

    @Test
    fun `setItems move parent with children to bottom`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        val itemCount = "A".itemCount
        listItemDragCallback.simulateDrag(0, items.lastIndex - itemCount, itemCount)
        items.printList("After move 0 to ${items.lastIndex}")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

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
        listItemDragCallback.simulateDrag(3, 0, "D".itemCount)
        items.printList("After move 3 to 0")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B")
        "D".assertChildren("E")
    }

    @Test
    fun `setItems child to other parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listItemDragCallback.simulateDrag(3, 1, 1)
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

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
        listItemDragCallback.simulateDrag(5, 3, 1)
        items.printList("After move 5 to 3")
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D", "E", "F")
    }

    @Test
    fun `setItems child to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listItemDragCallback.simulateDrag(3, 0, 1)
        val itemsBeforeMove = (changeHistory.lookUp() as ListMoveChange).oldValue

        listManager.setItems(itemsBeforeMove)

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
        items.printList("After move 4 to 2")

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

        val itemCount = "A".itemCount
        listItemDragCallback.simulateDrag(0, items.lastIndex - itemCount + 1, itemCount)
        items.printList("After move 0 to ${items.lastIndex}")
        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
    }

    @Test
    fun `endDrag parent with children to top`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")

        listItemDragCallback.simulateDrag(3, 0, "D".itemCount)
        items.printList("After move 3 to 0")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "D".assertChildren("E", "F")
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

    @Test
    fun `endDrag drag child to end of list`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listItemDragCallback.simulateDrag(3, items.lastIndex, 1)
        items.assertOrder("A", "B", "C", "E", "F", "D")
    }

    @Test
    fun `endDrag drag child one below`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true)
        listItemDragCallback.simulateDrag(3, 4, 1)
        items.assertOrder("A", "B", "C", "E", "D", "F")
        "E".assertChildren("D")
    }

    @Test
    fun `endDrag last unchecked child from otherwise checked parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeChecked(3, true)

        listItemDragCallback.simulateDrag(4, 1, 1)

        items.assertOrder("A", "E", "B", "C", "D", "F")
        "A".assertChildren("E")
        "E".assertIsNotChecked()
        "C".assertChildren("D")
        "C".assertIsChecked()
        "D".assertIsChecked()
    }

    @Test
    fun `endDrag first unchecked child from otherwise checked parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeChecked(4, true)

        listItemDragCallback.simulateDrag(3, 1, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        "D".assertIsNotChecked()
        "C".assertChildren("E")
        "C".assertIsChecked()
        "E".assertIsChecked()
    }

    @Test
    fun `endDrag unchecked child from otherwise checked parent with auto-sort`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeChecked(3, true)

        listItemDragCallback.simulateDrag(4, 1, 1)

        items.assertOrder("A", "E", "B", "F")
        itemsChecked!!.assertOrder("C", "D")
        "A".assertChildren("E")
        "E".assertIsNotChecked()
    }

    @Test
    fun `endDrag unchecked child into checked parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeChecked(4, true)

        listItemDragCallback.simulateDrag(1, 4, 1)

        items.assertOrder("A", "C", "D", "E", "B")
        "D".assertChildren("E", "B")
        "D".assertIsNotChecked()
        "E".assertIsChecked()
        "B".assertIsNotChecked()
    }

    @Test
    fun `endDrag parent with child below other parent keeps it parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true, false)

        listItemDragCallback.simulateDrag(0, 2, "A".itemCount)

        items.assertOrder("C", "D", "A", "B", "E")
        "D".assertChildren()
        "A".assertIsParent()
        "A".assertChildren("B")
    }

    // endregion

}
