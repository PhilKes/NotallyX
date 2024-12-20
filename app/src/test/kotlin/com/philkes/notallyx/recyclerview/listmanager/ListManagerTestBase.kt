package com.philkes.notallyx.recyclerview.listmanager

import android.view.inputmethod.InputMethodManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListItemDragCallback
import com.philkes.notallyx.presentation.view.note.listitem.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.find
import com.philkes.notallyx.presentation.view.note.listitem.sorting.indexOfFirst
import com.philkes.notallyx.presentation.viewmodel.preference.EnumPreference
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.test.assertChildren
import com.philkes.notallyx.test.createListItem
import com.philkes.notallyx.test.mockAndroidLog
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

open class ListManagerTestBase {

    lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: RecyclerView.Adapter<*>
    protected lateinit var inputMethodManager: InputMethodManager
    protected lateinit var changeHistory: ChangeHistory
    protected lateinit var listItemVH: ListItemVH
    protected lateinit var preferences: NotallyXPreferences
    protected lateinit var listItemDragCallback: ListItemDragCallback

    protected lateinit var items: ListItemSortedList

    lateinit var listManager: ListManager

    @get:Rule val rule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        mockAndroidLog()
        recyclerView = mock(RecyclerView::class.java)
        adapter = mock(RecyclerView.Adapter::class.java)
        inputMethodManager = mock(InputMethodManager::class.java)
        changeHistory = ChangeHistory()
        listItemVH = mock(ListItemVH::class.java)
        preferences = mock(NotallyXPreferences::class.java)
        listManager = ListManager(recyclerView, changeHistory, preferences, inputMethodManager) {}
        listManager.adapter = adapter as RecyclerView.Adapter<ListItemVH>
        // Prepare view holder
        `when`(recyclerView.findViewHolderForAdapterPosition(anyInt())).thenReturn(listItemVH)
    }

    protected fun setSorting(sorting: ListItemSort) {
        val sortCallback =
            when (sorting) {
                ListItemSort.AUTO_SORT_BY_CHECKED -> ListItemSortedByCheckedCallback(adapter)
                else -> ListItemNoSortCallback(adapter)
            }
        items = ListItemSortedList(sortCallback)
        if (sortCallback is ListItemSortedByCheckedCallback) {
            sortCallback.setList(items)
        }
        items.init(
            createListItem("A", id = 0, order = 0),
            createListItem("B", id = 1, order = 1),
            createListItem("C", id = 2, order = 2),
            createListItem("D", id = 3, order = 3),
            createListItem("E", id = 4, order = 4),
            createListItem("F", id = 5, order = 5),
        )
        listManager.initList(items)
        listItemDragCallback = ListItemDragCallback(1.0f, listManager)
        val listItemSortingPreference = mock(EnumPreference::class.java)
        `when`(listItemSortingPreference.value).thenReturn(sorting)
        `when`(preferences.listItemSorting)
            .thenReturn(listItemSortingPreference as EnumPreference<ListItemSort>)
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
        assertEquals("order", expected, items.find { it.body == this }!!.order)
    }

    protected fun String.assertPosition(expected: Int) {
        assertEquals("position in items", expected, items.indexOfFirst { it.body == this })
    }

    protected fun String.assertIsParent() {
        assertFalse(items.find { it.body == this }!!.isChild)
    }

    protected fun String.assertIsChild() {
        assertTrue(items.find { it.body == this }!!.isChild)
    }

    protected val String.itemCount: Int
        get() {
            return items.find { it.body == this }!!.itemCount
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
