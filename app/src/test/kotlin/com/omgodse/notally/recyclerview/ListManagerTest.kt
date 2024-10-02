package com.omgodse.notally.recyclerview

import com.omgodse.notally.changehistory.ChangeCheckedForAllChange
import com.omgodse.notally.changehistory.DeleteCheckedChange
import com.omgodse.notally.changehistory.ListAddChange
import com.omgodse.notally.changehistory.ListCheckedChange
import com.omgodse.notally.changehistory.ListDeleteChange
import com.omgodse.notally.changehistory.ListIsChildChange
import com.omgodse.notally.changehistory.ListMoveChange
import com.omgodse.notally.preferences.ListItemSorting
import com.omgodse.notally.room.ListItem
import com.omgodse.notally.test.assert
import com.omgodse.notally.test.assertChecked
import com.omgodse.notally.test.assertChildren
import com.omgodse.notally.test.assertOrder
import com.omgodse.notally.test.createListItem
import com.omgodse.notally.test.mockPreferences
import com.omgodse.notally.test.printList
import com.omgodse.notally.test.simulateDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class ListManagerTest : ListManagerTestBase() {

    // region add
    @Test
    fun `add item at default position`() {
        mockPreferences(preferences)
        val item = createListItem("Test")
        val newItem = item.clone() as ListItem
        listManager.add(item = item)

        "Test".assertPosition(6)
        items.assertSize(7)
        verify(adapter).notifyItemInserted(6)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(6, newItem)
    }

    @Test
    fun `add default item before child item`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        reset(adapter)
        listManager.add(1)

        items.assertOrder("A", "", "B")
        "A".assertChildren("", "B")
        items.assertSize(7)
        verify(adapter).notifyItemInserted(1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(1, listManager.defaultNewItem(1))
    }

    @Test
    fun `add default item after child item`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        reset(adapter)
        listManager.add(2)

        items.assertOrder("A", "B", "")
        "A".assertChildren("B", "")
        items.assertSize(7)
        verify(adapter).notifyItemInserted(2)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(2, listManager.defaultNewItem(1))
    }

    @Test
    fun `add checked item with correct sortingPosition`() {
        mockPreferences(preferences)
        val itemToAdd = ListItem("Test", true, false, null, mutableListOf())
        val newItem = itemToAdd.clone() as ListItem
        listManager.add(0, item = itemToAdd)

        "Test".assertPosition(0)
        "Test".assertIsChecked()
        "Test".assertSortingPosition(0)
        items.assertSize(7)
        verify(adapter).notifyItemInserted(0)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(0, newItem)
    }

    @Test
    fun `add checked item with correct sortingPosition when auto-sort enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        val itemToAdd = ListItem("Test", true, false, null, mutableListOf())
        val newItem = itemToAdd.clone() as ListItem
        listManager.add(position = 0, item = itemToAdd)

        "Test".assertPosition(6)
        "Test".assertIsChecked()
        "Test".assertSortingPosition(0)
        items.assertSize(7)
        verify(adapter).notifyItemInserted(0)
        verify(adapter).notifyItemMoved(0, 6)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(0, newItem)
    }

    @Test
    fun `add item with children`() {
        mockPreferences(preferences)
        val childItem1 = ListItem("Child1", false, true, null, mutableListOf())
        val childItem2 = ListItem("Child2", false, true, null, mutableListOf())
        val parentItem =
            ListItem("Parent", false, false, null, mutableListOf(childItem1, childItem2))
        val newItem = parentItem.clone() as ListItem
        items.printList("Before add")

        listManager.add(item = parentItem)
        items.printList("After add")

        "Parent".assertChildren("Child1", "Child2")
        "Parent".assertPosition(6)
        "Child1".assertPosition(7)
        "Child2".assertPosition(8)
        "Parent".assertSortingPosition(6)
        "Child1".assertSortingPosition(7)
        "Child2".assertSortingPosition(8)
        items.assertSize(9)

        verify(adapter).notifyItemInserted(6)
        verify(adapter).notifyItemInserted(7)
        verify(adapter).notifyItemInserted(8)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListAddChange).assert(6, newItem)
    }

    // endregion

    // region delete

    @Test
    fun `delete first item from list unforced does not delete`() {
        mockPreferences(preferences)
        val deletedItem = listManager.delete(position = 0, force = false, allowFocusChange = false)

        assertNull(deletedItem)
        items.assertSize(6)
        verify(adapter, never()).notifyItemRangeRemoved(0, 1)
        verifyNoMoreInteractions(adapter)
        assertFalse(changeHistory.canUndo())
    }

    @Test
    fun `delete first item from list forced`() {
        mockPreferences(preferences)
        val deletedItem = listManager.delete(position = 0, force = true)

        assertEquals("A", deletedItem!!.body)
        items.assertSize(5)
        verify(adapter).notifyItemRangeRemoved(0, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListDeleteChange).assert(0, deletedItem)
    }

    @Test
    fun `delete item with children from list`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        reset(adapter)

        val deletedItem = listManager.delete(position = 0, force = true)!!

        items.assertSize(4)
        assertEquals("A", deletedItem.body)
        deletedItem.assertChildren("B")
        verify(adapter).notifyItemRangeRemoved(0, 2) // 1 parent + 1 child
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListDeleteChange).assert(0, deletedItem)
    }

    @Test
    fun `delete at invalid position`() {
        mockPreferences(preferences)
        val deletedItem = listManager.delete(10)

        assertNull(deletedItem)
        verify(adapter, never()).notifyItemRangeRemoved(anyInt(), anyInt())
        assertFalse(changeHistory.canUndo())
    }

    // endregion

    // region deleteCheckedItems

    @Test
    fun `delete checked items`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        reset(adapter)
        val itemsBeforeDelete = items.toMutableList()

        listManager.deleteCheckedItems()

        items.assertOrder("B", "E", "F")
        verify(adapter).notifyItemRangeRemoved(0, 1)
        verify(adapter).notifyItemRangeRemoved(2, 2)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as DeleteCheckedChange).assert(itemsBeforeDelete)
    }

    @Test
    fun `delete checked items with auto-sort enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        reset(adapter)
        val itemsBeforeDelete = items.toMutableList()

        listManager.deleteCheckedItems()

        items.assertOrder("B", "E", "F")
        verify(adapter).notifyItemRangeRemoved(3, 3)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as DeleteCheckedChange).assert(itemsBeforeDelete)
    }

    // endregion

    // region move
    @Test
    fun `move parent without children`() {
        mockPreferences(preferences)
        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B")
        assertEquals(1, newPosition)
        verify(adapter).notifyItemMoved(3, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " D")
    }

    @Test
    fun `move parent with children into other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.move(3, 2)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertChildren()
        assertEquals(2, newPosition)
        verify(adapter).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(4, 3)
        verify(adapter).notifyItemChanged(2)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 2, 2, " D(E)")
    }

    @Test
    fun `move parent with children to bottom`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.move(0, items.lastIndex)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
        assertEquals(items.lastIndex - 2, newPosition)
        verify(adapter).notifyItemMoved(0, items.lastIndex - 2)
        verify(adapter).notifyItemMoved(1, items.lastIndex - 1)
        verify(adapter).notifyItemMoved(2, items.lastIndex)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(0, 5, 3, " A(B,C)")
    }

    @Test
    fun `move parent with children to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.move(3, 0)
        items.printList("After move 3 to 0")

        items.assertOrder("D", "E", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
        assertEquals(0, newPosition)
        verify(adapter).notifyItemMoved(3, 0)
        verify(adapter).notifyItemMoved(4, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 0, 0, " D(E)")
    }

    @Test
    fun `move child to other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        reset(adapter)

        val newPosition = listManager.move(3, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        assertEquals(1, newPosition)
        verify(adapter).notifyItemMoved(3, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " > D")
    }

    @Test
    fun `move child above other child`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        reset(adapter)

        val newPosition = listManager.move(5, 3)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
        assertEquals(3, newPosition)
        verify(adapter).notifyItemMoved(5, 3)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(5, 3, 3, " > F")
    }

    @Test
    fun `move child to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        reset(adapter)

        val newPosition = listManager.move(3, 0)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
        assertEquals(0, newPosition)
        verify(adapter).notifyItemMoved(3, 0)
        verify(adapter).notifyItemChanged(0)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `dont move parent into own children`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true)
        listManager.changeIsChild(4, true)
        reset(adapter)

        val newPosition = listManager.move(2, 3)

        "C".assertChildren("D", "E")
        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `dont move parent under checked item if auto-sort enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeChecked(5, true)
        reset(adapter)

        val newPosition = listManager.move(2, 5)

        assertNull(newPosition)
        items.assertOrder("A", "B", "C", "D", "E", "F")
        verifyNoMoreInteractions(adapter)
    }

    // endregion

    // region revertMove

    @Test
    fun `revertMove parent without children`() {
        mockPreferences(preferences)
        val newPosition = listManager.move(1, 4)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove
        reset(adapter)

        listManager.revertMove(newPosition, 1, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        verify(adapter).notifyItemMoved(newPosition, 1)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove parent with children into other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(2, true, false)
        val newPosition = listManager.move(1, 3)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove
        reset(adapter)

        listManager.revertMove(newPosition, 1, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "B".assertChildren("C")
        "D".assertChildren()
        verify(adapter).notifyItemMoved(newPosition, 1)
        verify(adapter).notifyItemMoved(newPosition + 1, 2)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove move parent with children to bottom`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        val newPosition = listManager.move(0, items.lastIndex)!!
        items.printList("After move 0 to ${items.lastIndex}")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove
        reset(adapter)

        listManager.revertMove(newPosition, 0, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B", "C")
        "F".assertChildren()
        verify(adapter).notifyItemMoved(3, 0)
        verify(adapter).notifyItemMoved(4, 1)
        verify(adapter).notifyItemMoved(5, 2)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove parent with children to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        items.printList("Before")
        val newPosition = listManager.move(3, 0)!!
        items.printList("After move 3 to 0")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove
        reset(adapter)

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "A".assertChildren("B")
        "D".assertChildren("E")
        verify(adapter).notifyItemMoved(0, 3)
        verify(adapter).notifyItemMoved(1, 4)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove child to other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        val newPosition = listManager.move(3, 1)!!
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove
        reset(adapter)

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
        "A".assertChildren()
        verify(adapter).notifyItemMoved(1, 3)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove child above other child`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        items.printList("Before")
        val newPosition = listManager.move(5, 3)!!
        reset(adapter)
        items.printList("After move 5 to 3")
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 5, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D", "E", "F")
        verify(adapter).notifyItemMoved(3, 5)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `revertMove child to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        val newPosition = listManager.move(3, 0)!!
        reset(adapter)
        val itemBeforeMove = (changeHistory.lookUp() as ListMoveChange).itemBeforeMove

        listManager.revertMove(newPosition, 3, itemBeforeMove)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        "C".assertChildren("D")
        verify(adapter).notifyItemMoved(0, 3)
        verify(adapter).notifyItemChanged(3)
        verifyNoMoreInteractions(adapter)
    }

    // endregion

    // region endDrag

    @Test
    fun `endDrag parent without children`() {
        mockPreferences(preferences)
        val newPosition = listManager.simulateDrag(3, 1)

        items.assertOrder("A", "D", "B")
        assertEquals(1, newPosition)
        verify(adapter).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(2, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " D")
    }

    @Test
    fun `endDrag parent with children into other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        listManager.changeIsChild(4, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(3, 2)
        items.printList("After move 3 to 2")

        items.assertOrder("A", "B", "D", "E", "C", "F")
        "A".assertChildren("B", "D", "E", "C")
        "D".assertChildren()
        assertEquals(2, newPosition)
        verify(adapter).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(4, 3)
        verify(adapter).notifyItemChanged(2)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 2, 2, " D(E)")
    }

    @Test
    fun `endDrag parent with children to bottom`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(2, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(0, items.lastIndex)
        items.printList("After move 0 to ${items.lastIndex}")

        items.assertOrder("D", "E", "F", "A", "B", "C")
        "A".assertChildren("B", "C")
        "D".assertChildren()
        assertEquals(items.lastIndex - 2, newPosition)
        verify(adapter, times(3)).notifyItemMoved(2, 3)
        verify(adapter, times(2)).notifyItemMoved(1, 2)
        verify(adapter).notifyItemMoved(0, 1)
        verify(adapter, times(2)).notifyItemMoved(3, 4)
        verify(adapter).notifyItemMoved(4, 5)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(0, 5, 3, " A(B,C)")
    }

    @Test
    fun `endDrag parent with children to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true, false)
        listManager.changeIsChild(4, true, false)
        reset(adapter)
        items.printList("Before")

        val newPosition = listManager.simulateDrag(3, 0)
        items.printList("After move 3 to 0")

        items.assertOrder("D", "E", "A", "B", "C", "F")
        "D".assertChildren("E")
        "A".assertChildren("B")
        assertEquals(0, newPosition)
        verify(adapter, times(2)).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(4, 3)
        verify(adapter, times(2)).notifyItemMoved(2, 1)
        verify(adapter).notifyItemMoved(1, 0)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 0, 0, " D(E)")
    }

    @Test
    fun `endDrag child to other parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        reset(adapter)

        val newPosition = listManager.simulateDrag(3, 1)

        items.assertOrder("A", "D", "B", "C", "E", "F")
        "A".assertChildren("D")
        assertEquals(1, newPosition)
        verify(adapter).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(2, 1)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(3, 1, 1, " > D")
    }

    @Test
    fun `endDrag child above other child`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        listManager.changeIsChild(4, true, false)
        listManager.changeIsChild(5, true, false)
        reset(adapter)

        val newPosition = listManager.simulateDrag(5, 3)

        items.assertOrder("A", "B", "C", "F", "D", "E")
        "C".assertChildren("F", "D", "E")
        assertEquals(3, newPosition)
        verify(adapter).notifyItemMoved(5, 4)
        verify(adapter).notifyItemMoved(4, 3)
        verifyNoMoreInteractions(adapter)
        (changeHistory.lookUp() as ListMoveChange).assert(5, 3, 3, " > F")
    }

    @Test
    fun `endDrag child to top`() {
        mockPreferences(preferences)
        listManager.changeIsChild(3, true, false)
        reset(adapter)

        val newPosition = listManager.simulateDrag(3, 0)

        items.assertOrder("D", "A", "B", "C", "E", "F")
        "D".assertIsParent()
        "C".assertChildren()
        assertEquals(0, newPosition)
        verify(adapter).notifyItemMoved(3, 2)
        verify(adapter).notifyItemMoved(2, 1)
        verify(adapter).notifyItemMoved(1, 0)
        verify(adapter).notifyItemChanged(0)
        verifyNoMoreInteractions(adapter)
    }

    // endregion

    // region changeChecked

    @Test
    fun `changeChecked checks item`() {
        mockPreferences(preferences)
        val positionAfter = listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertSortingPosition(0)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0, 0)
        verify(adapter).notifyItemRangeChanged(0, 1, null)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked unchecks item and updates sortingPosition`() {
        mockPreferences(preferences)
        listManager.changeChecked(0, true)
        reset(adapter)
        listManager.changeChecked(0, checked = false, pushChange = true)

        "A".assertIsNotChecked()
        "A".assertSortingPosition(0)
        verify(adapter).notifyItemRangeChanged(0, 1, null)
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 0, 0)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked does nothing when state is unchanged`() {
        mockPreferences(preferences)
        listManager.changeChecked(0, true)
        reset(adapter)

        val positionAfter = listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "A".assertSortingPosition(0)
        assertEquals(0, positionAfter)
        verify(adapter, never()).notifyItemRangeRemoved(anyInt(), anyInt())
        verify(adapter, never()).notifyItemRangeInserted(anyInt(), anyInt())
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked on child item`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        reset(adapter)

        val positionAfter = listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsNotChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "B".assertSortingPosition(1)
        verify(adapter).notifyItemChanged(1)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 1, 1)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked on parent item checks all children`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        reset(adapter)

        val positionAfter = listManager.changeChecked(0, checked = true, pushChange = true)

        "A".assertIsChecked()
        "B".assertIsChecked()
        "C".assertIsChecked()
        "A".assertChildren("B", "C")
        "A".assertSortingPosition(0)
        "B".assertSortingPosition(1)
        "C".assertSortingPosition(2)
        verify(adapter).notifyItemRangeChanged(0, 3, null)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0, 0)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked on child item and does not move when auto-sort is enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        reset(adapter)

        listManager.changeChecked(1, checked = true, pushChange = true)

        "A".assertIsNotChecked()
        "A".assertChildren("B")
        "B".assertIsChecked()
        "B".assertSortingPosition(1)
        verify(adapter).notifyItemChanged(1)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 1, 1)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked true on parent item checks all children and moves when auto-sort is enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        reset(adapter)

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
        verify(adapter).notifyItemRangeChanged(0, 3, null)
        verify(adapter, times(3)).notifyItemMoved(5, 0)
        (changeHistory.lookUp() as ListCheckedChange).assert(true, 0, 3)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeChecked false on parent item unchecks all children and moves when auto-sort is enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(0, checked = true, pushChange = true)
        reset(adapter)

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
        verify(adapter, times(3)).notifyItemRangeChanged(0, 1, null)
        verify(adapter, times(3)).notifyItemMoved(5, 0)
        (changeHistory.lookUp() as ListCheckedChange).assert(false, 3, 0)
        verifyNoMoreInteractions(adapter)
    }

    // endregion

    // region changeCheckedForAll

    @Test
    fun `changeCheckedForAll true checks all unchecked items`() {
        mockPreferences(preferences)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)
        reset(adapter)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(
            true,
            listOf(0, 2, 4, 5),
            listOf(0, 2, 4, 5),
        )
        verify(adapter).notifyItemChanged(0)
        verify(adapter).notifyItemChanged(2)
        verify(adapter).notifyItemChanged(4)
        verify(adapter).notifyItemChanged(5)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeCheckedForAll false unchecks all unchecked items`() {
        mockPreferences(preferences)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(1, true)
        listManager.changeChecked(3, true)
        reset(adapter)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(
            false,
            listOf(1, 2, 3),
            listOf(1, 2, 3),
        )
        verify(adapter).notifyItemChanged(1)
        verify(adapter).notifyItemChanged(2)
        verify(adapter).notifyItemChanged(3)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeCheckedForAll true correct order with auto-sort enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        reset(adapter)

        listManager.changeCheckedForAll(true)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(true, true, true, true, true, true)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(
            true,
            listOf(0, 1, 2),
            listOf(1, 4, 5),
        )
        verify(adapter).notifyItemChanged(0)
        verify(adapter).notifyItemChanged(1)
        verify(adapter).notifyItemChanged(2)
        verify(adapter, times(2)).notifyItemMoved(5, 1)
        verify(adapter).notifyItemMoved(5, 0)
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `changeCheckedForAll false correct order with auto-sort enabled`() {
        mockPreferences(preferences, ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        reset(adapter)

        listManager.changeCheckedForAll(false)

        items.assertOrder("A", "B", "C", "D", "E", "F")
        items.assertChecked(false, false, false, false, false, false)
        (changeHistory.lookUp() as ChangeCheckedForAllChange).assert(
            false,
            listOf(3, 4, 5),
            listOf(0, 2, 3),
        )
        verify(adapter).notifyItemChanged(3)
        verify(adapter).notifyItemChanged(4)
        verify(adapter).notifyItemChanged(5)
        verify(adapter, times(2)).notifyItemMoved(5, 1)
        verify(adapter).notifyItemMoved(5, 0)
        verifyNoMoreInteractions(adapter)
    }

    // endregion

    // region changeIsChild

    @Test
    fun `changeIsChild changes parent to child and pushes change`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, isChild = true)

        items.assertOrder("A", "B")
        "A".assertChildren("B")
        verify(adapter).notifyItemChanged(1)
        (changeHistory.lookUp() as ListIsChildChange).assert(true, 1, 1)
    }

    @Test
    fun `changeIsChild changes child to parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, isChild = true)
        reset(adapter)

        listManager.changeIsChild(1, isChild = false)

        "A".assertIsParent()
        "B".assertIsParent()
        verify(adapter).notifyItemChanged(1)
        (changeHistory.lookUp() as ListIsChildChange).assert(false, 1, 1)
    }

    @Test
    fun `changeIsChild adds all child items when item becomes a parent`() {
        mockPreferences(preferences)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        reset(adapter)

        listManager.changeIsChild(1, isChild = false)

        "A".assertChildren()
        "B".assertChildren("C")
        verify(adapter).notifyItemChanged(1)
        (changeHistory.lookUp() as ListIsChildChange).assert(false, 1, 1)
    }

    // endregion

}
