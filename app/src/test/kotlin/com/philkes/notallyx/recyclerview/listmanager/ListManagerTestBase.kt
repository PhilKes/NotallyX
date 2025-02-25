package com.philkes.notallyx.recyclerview.listmanager

import android.view.inputmethod.InputMethodManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListItemDragCallback
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.adapter.CheckedListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.init
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemParentSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.SortedItemsList
import com.philkes.notallyx.presentation.viewmodel.preference.EnumPreference
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.test.assertChildren
import com.philkes.notallyx.test.createListItem
import com.philkes.notallyx.test.mockAndroidLog
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doAnswer

open class ListManagerTestBase {

    lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: ListItemAdapter
    protected lateinit var adapterChecked: CheckedListItemAdapter
    protected lateinit var inputMethodManager: InputMethodManager
    protected lateinit var changeHistory: ChangeHistory
    protected lateinit var listItemVH: ListItemVH
    protected lateinit var preferences: NotallyXPreferences
    protected lateinit var listItemDragCallback: ListItemDragCallback
    private lateinit var itemsInternal: MutableList<ListItem>
    protected var itemsChecked: SortedItemsList? = null

    lateinit var listManager: ListManager

    @get:Rule val rule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        mockAndroidLog()
        recyclerView = mock(RecyclerView::class.java)
        adapter = mock(ListItemAdapter::class.java)
        adapterChecked = mock(CheckedListItemAdapter::class.java)
        inputMethodManager = mock(InputMethodManager::class.java)
        changeHistory = ChangeHistory()
        listItemVH = mock(ListItemVH::class.java)
        preferences = mock(NotallyXPreferences::class.java)
        listManager =
            ListManager(recyclerView, changeHistory, preferences, inputMethodManager, {}) {}
        // Prepare view holder
        `when`(recyclerView.findViewHolderForAdapterPosition(anyInt())).thenReturn(listItemVH)
    }

    protected fun setSorting(sorting: ListItemSort) {
        itemsInternal = mutableListOf<ListItem>()
        if (sorting == ListItemSort.AUTO_SORT_BY_CHECKED) {
            val sortCallback = ListItemParentSortCallback(adapterChecked)
            itemsChecked = SortedItemsList(sortCallback)
        }
        itemsInternal.addAll(
            listOf(
                    createListItem("A", id = 0, order = 0),
                    createListItem("B", id = 1, order = 1),
                    createListItem("C", id = 2, order = 2),
                    createListItem("D", id = 3, order = 3),
                    createListItem("E", id = 4, order = 4),
                    createListItem("F", id = 5, order = 5),
                )
                .init()
        )
        `when`(adapter.items).thenAnswer { itemsInternal }
        doAnswer { invocation ->
                val listArgument = invocation.getArgument<MutableList<ListItem>>(0)
                itemsInternal = listArgument
            }
            .`when`(adapter)
            .submitList(any())

        listManager.init(adapter, itemsChecked, adapterChecked)
        adapter.submitList(items)
        listItemDragCallback = ListItemDragCallback(1.0f, listManager)
        val listItemSortingPreference = mock(EnumPreference::class.java)
        `when`(listItemSortingPreference.value).thenReturn(sorting)
        `when`(preferences.listItemSorting)
            .thenReturn(listItemSortingPreference as EnumPreference<ListItemSort>)
    }

    protected val items: MutableList<ListItem>
        get() = itemsInternal

    protected operator fun List<ListItem>.get(body: String): ListItem {
        return this.find { it.body == body }!!
    }

    protected fun findItem(function: (item: ListItem) -> Boolean): ListItem? {
        return items.find(function) ?: itemsChecked?.find(function)
    }

    protected fun String.assertChildren(vararg childrenBodies: String) {
        findItem { it.body == this }!!.assertChildren(*childrenBodies)
    }

    protected fun String.assertIsChecked() {
        assertTrue("checked", findItem { it.body == this }!!.checked)
    }

    protected fun String.assertIsNotChecked() {
        assertFalse("checked", findItem { it.body == this }!!.checked)
    }

    protected fun String.assertOrder(expected: Int) {
        assertEquals("order", expected, findItem { it.body == this }!!.order)
    }

    protected fun String.assertPosition(expected: Int) {
        assertEquals("position in items", expected, items.indexOfFirst { it.body == this })
    }

    protected fun String.assertIsParent() {
        assertFalse(findItem { it.body == this }!!.isChild)
    }

    protected fun String.assertIsChild() {
        assertTrue(findItem { it.body == this }!!.isChild)
    }

    protected val String.itemCount: Int
        get() {
            return findItem { it.body == this }!!.itemCount
        }

    protected fun ListManager.addWithChildren(
        position: Int = items.size,
        parentBody: String,
        vararg childrenBodies: String,
    ) {
        val children =
            childrenBodies.map { createListItem(body = it, isChild = true) }.toMutableList()
        this.add(position, createListItem(body = parentBody, children = children))
    }
}
