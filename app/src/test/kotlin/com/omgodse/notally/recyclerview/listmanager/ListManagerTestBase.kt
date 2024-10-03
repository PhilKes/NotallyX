package com.omgodse.notally.recyclerview.listmanager

import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.omgodse.notally.changehistory.ChangeHistory
import com.omgodse.notally.preferences.BetterLiveData
import com.omgodse.notally.preferences.ListItemSorting
import com.omgodse.notally.preferences.Preferences
import com.omgodse.notally.recyclerview.ListItemNoSortCallback
import com.omgodse.notally.recyclerview.ListItemSortedByCheckedCallback
import com.omgodse.notally.recyclerview.ListItemSortedList
import com.omgodse.notally.recyclerview.ListManager
import com.omgodse.notally.recyclerview.find
import com.omgodse.notally.recyclerview.indexOfFirst
import com.omgodse.notally.recyclerview.viewholder.MakeListVH
import com.omgodse.notally.room.ListItem
import com.omgodse.notally.test.assertChildren
import com.omgodse.notally.test.createListItem
import com.omgodse.notally.test.mockAndroidLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

open class ListManagerTestBase {

    lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: RecyclerView.Adapter<*>
    protected lateinit var inputMethodManager: InputMethodManager
    protected lateinit var changeHistory: ChangeHistory
    protected lateinit var makeListVH: MakeListVH
    protected lateinit var preferences: Preferences

    protected lateinit var items: ListItemSortedList

    lateinit var listManager: ListManager

    @Before
    fun setUp() {
        mockAndroidLog()
        recyclerView = mock(RecyclerView::class.java)
        adapter = mock(RecyclerView.Adapter::class.java)
        inputMethodManager = mock(InputMethodManager::class.java)
        changeHistory = ChangeHistory() {}
        makeListVH = mock(MakeListVH::class.java)
        preferences = mock(Preferences::class.java)
        listManager = ListManager(recyclerView, changeHistory, preferences, inputMethodManager)
        listManager.adapter = adapter as RecyclerView.Adapter<MakeListVH>
        // Prepare view holder
        `when`(recyclerView.findViewHolderForAdapterPosition(anyInt())).thenReturn(makeListVH)
    }

    protected fun setSorting(sorting: String) {
        val sortCallback =
            when (sorting) {
                ListItemSorting.autoSortByChecked -> ListItemSortedByCheckedCallback(adapter)
                else -> ListItemNoSortCallback(adapter)
            }
        items = ListItemSortedList(sortCallback)
        if (sortCallback is ListItemSortedByCheckedCallback) {
            sortCallback.setList(items)
        }
        items.addAll(
            createListItem("A", id = 0, sortingPosition = 0),
            createListItem("B", id = 1, sortingPosition = 1),
            createListItem("C", id = 2, sortingPosition = 2),
            createListItem("D", id = 3, sortingPosition = 3),
            createListItem("E", id = 4, sortingPosition = 4),
            createListItem("F", id = 5, sortingPosition = 5),
        )
        listManager.initList(items)
        `when`(preferences.listItemSorting).thenReturn(BetterLiveData(sorting))
    }

    protected operator fun List<ListItem>.get(body: String): ListItem {
        return this.find { it.body == body }!!
    }

    protected fun <E> SortedList<E>.assertSize(expected: Int) {
        assertEquals("size", expected, this.size())
    }

    protected fun String.assertChildren(vararg childrenBodies: String) {
        items.find { it.body == this }!!.assertChildren(*childrenBodies)
    }

    protected fun String.assertIsChecked() {
        assertTrue("checked", items.find { it.body == this }!!.checked)
    }

    protected fun String.assertIsNotChecked() {
        assertFalse("checked", items.find { it.body == this }!!.checked)
    }

    protected fun String.assertSortingPosition(expected: Int) {
        assertEquals("sortingPosition", expected, items.find { it.body == this }!!.sortingPosition)
    }

    protected fun String.assertPosition(expected: Int) {
        assertEquals("position in items", expected, items.indexOfFirst { it.body == this })
    }

    protected fun String.assertIsParent() {
        assertFalse(items.find { it.body == this }!!.isChild)
    }

    protected fun ListManager.addWithChildren(
        position: Int = items.size(),
        parentBody: String,
        vararg childrenBodies: String,
    ) {
        val children =
            childrenBodies.map { createListItem(body = it, isChild = true) }.toMutableList()
        this.add(position, createListItem(body = parentBody, children = children))
    }
}
