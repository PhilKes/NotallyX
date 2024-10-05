package com.omgodse.notally.recyclerview.listmanager

import com.omgodse.notally.changehistory.DeleteCheckedChange
import com.omgodse.notally.changehistory.ListAddChange
import com.omgodse.notally.changehistory.ListDeleteChange
import com.omgodse.notally.model.ListItem
import com.omgodse.notally.preferences.ListItemSorting
import com.omgodse.notally.test.assert
import com.omgodse.notally.test.assertChildren
import com.omgodse.notally.test.assertOrder
import com.omgodse.notally.test.createListItem
import com.omgodse.notally.test.find
import com.omgodse.notally.test.printList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ListManagerAddDeleteTest : ListManagerTestBase() {

    // region add
    @Test
    fun `add item at default position`() {
        setSorting(ListItemSorting.noAutoSort)
        val item = createListItem("Test")
        val newItem = item.clone() as ListItem
        listManager.add(item = item)

        "Test".assertPosition(6)
        items.assertSize(7)
        (changeHistory.lookUp() as ListAddChange).assert(6, newItem)
    }

    @Test
    fun `add default item before child item`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true)
        listManager.add(1)

        items.assertOrder("A", "", "B")
        "A".assertChildren("", "B")
        items.assertSize(7)
        (changeHistory.lookUp() as ListAddChange).assert(1, listManager.defaultNewItem(1))
    }

    @Test
    fun `add default item after child item`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true)
        listManager.add(2)

        items.assertOrder("A", "B", "")
        "A".assertChildren("B", "")
        items.assertSize(7)
        (changeHistory.lookUp() as ListAddChange).assert(2, listManager.defaultNewItem(1))
    }

    @Test
    fun `add checked item with correct order`() {
        setSorting(ListItemSorting.noAutoSort)
        val itemToAdd = ListItem("Test", true, false, null, mutableListOf())
        val newItem = itemToAdd.clone() as ListItem
        listManager.add(0, item = itemToAdd)

        "Test".assertPosition(0)
        "Test".assertIsChecked()
        "Test".assertSortingPosition(0)
        items.assertSize(7)
        (changeHistory.lookUp() as ListAddChange).assert(0, newItem)
    }

    @Test
    fun `add checked item with correct order when auto-sort enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        val itemToAdd = ListItem("Test", true, false, null, mutableListOf())
        val newItem = itemToAdd.clone() as ListItem
        listManager.add(position = 0, item = itemToAdd)

        "Test".assertPosition(6)
        "Test".assertIsChecked()
        "Test".assertSortingPosition(0)
        items.assertSize(7)
        (changeHistory.lookUp() as ListAddChange).assert(0, newItem)
    }

    @Test
    fun `add item with children`() {
        setSorting(ListItemSorting.noAutoSort)
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

        (changeHistory.lookUp() as ListAddChange).assert(6, newItem)
    }

    // endregion

    // region delete

    @Test
    fun `delete first item from list unforced does not delete`() {
        setSorting(ListItemSorting.noAutoSort)
        val deletedItem = listManager.delete(position = 0, force = false, allowFocusChange = false)

        assertNull(deletedItem)
        items.assertSize(6)
        assertFalse(changeHistory.canUndo())
    }

    @Test
    fun `delete first item from list forced`() {
        setSorting(ListItemSorting.noAutoSort)
        val deletedItem = listManager.delete(position = 0, force = true)

        assertEquals("A", deletedItem!!.body)
        items.assertSize(5)
        (changeHistory.lookUp() as ListDeleteChange).assert(0, deletedItem)
    }

    @Test
    fun `delete item with children from list`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(1, true)

        val deletedItem = listManager.delete(position = 0, force = true)!!

        items.assertSize(4)
        assertEquals("A", deletedItem.body)
        deletedItem.assertChildren("B")
        (changeHistory.lookUp() as ListDeleteChange).assert(0, deletedItem)
    }

    @Test
    fun `delete at invalid position`() {
        setSorting(ListItemSorting.noAutoSort)
        val deletedItem = listManager.delete(10)

        assertNull(deletedItem)
        assertFalse(changeHistory.canUndo())
    }

    // endregion

    // region deleteCheckedItems

    @Test
    fun `delete checked items`() {
        setSorting(ListItemSorting.noAutoSort)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        val deletedItems = items.find("A", "C")

        listManager.deleteCheckedItems()

        items.assertOrder("B", "E", "F")
        (changeHistory.lookUp() as DeleteCheckedChange).assert(deletedItems)
    }

    @Test
    fun `delete checked items with auto-sort enabled`() {
        setSorting(ListItemSorting.autoSortByChecked)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(2, true)
        listManager.changeChecked(0, true)
        val deletedItems = items.find("A", "C")
        listManager.deleteCheckedItems()

        items.assertOrder("B", "E", "F")
        (changeHistory.lookUp() as DeleteCheckedChange).assert(deletedItems)
    }

    // endregion
}
