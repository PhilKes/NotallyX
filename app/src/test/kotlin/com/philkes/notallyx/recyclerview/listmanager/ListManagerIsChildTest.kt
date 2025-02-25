package com.philkes.notallyx.recyclerview.listmanager

import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.test.assert
import com.philkes.notallyx.test.assertOrder
import com.philkes.notallyx.utils.changehistory.ListIsChildChange
import org.junit.Test

class ListManagerIsChildTest : ListManagerTestBase() {

    // region changeIsChild

    @Test
    fun `changeIsChild changes parent to child and pushes change`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, isChild = true)

        items.assertOrder("A", "B")
        "A".assertChildren("B")
        (changeHistory.lookUp() as ListIsChildChange).assert(true, 1)
    }

    @Test
    fun `changeIsChild changes child to parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, isChild = true)

        listManager.changeIsChild(1, isChild = false)

        "A".assertIsParent()
        "B".assertIsParent()
        (changeHistory.lookUp() as ListIsChildChange).assert(false, 1)
    }

    @Test
    fun `changeIsChild adds all child items when item becomes a parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)

        listManager.changeIsChild(1, isChild = false)

        "A".assertChildren()
        "B".assertChildren("C")
        (changeHistory.lookUp() as ListIsChildChange).assert(false, 1)
    }

    @Test
    fun `changeIsChild unchecks checked parent`() {
        setSorting(ListItemSort.NO_AUTO_SORT)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeChecked(1, true)
        listManager.changeChecked(2, true)

        listManager.changeIsChild(3, isChild = true)

        "A".assertChildren("B", "C", "D")
        "A".assertIsNotChecked()
        "D".assertIsNotChecked()
    }

    @Test
    fun `changeIsChild make unchecked parent child in otherwise checked parent with auto-sort`() {
        setSorting(ListItemSort.AUTO_SORT_BY_CHECKED)
        listManager.changeIsChild(1, true)
        listManager.changeIsChild(2, true)
        listManager.changeIsChild(3, true)
        listManager.changeChecked(1, true)
        listManager.changeChecked(2, true)

        listManager.changeIsChild(3, isChild = false)

        items.assertOrder("D", "E", "F")
        itemsChecked!!.assertOrder("A", "B", "C")
    }

    // endregion

}
