package com.philkes.notallyx.test

import android.util.Log
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.presentation.view.note.listitem.ListItemDragCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.find
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toReadableString
import com.philkes.notallyx.utils.changehistory.ListAddChange
import com.philkes.notallyx.utils.changehistory.ListDeleteChange
import com.philkes.notallyx.utils.changehistory.ListIsChildChange
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.Mockito

fun createListItem(
    body: String,
    checked: Boolean = false,
    isChild: Boolean = false,
    order: Int? = null,
    children: MutableList<ListItem> = mutableListOf(),
    id: Int = -1,
): ListItem {
    return ListItem(body, checked, isChild, order, children, id)
}

fun mockAndroidLog() {
    mockkStatic(Log::class)
    every { Log.v(any(), any()) } returns 0
    every { Log.d(any(), any()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
}

fun ListItemDragCallback.simulateDrag(positionFrom: Int, positionTo: Int, itemCount: Int) {
    this.reset()
    var from = positionFrom
    listManager.startDrag(from)
    if (positionFrom < positionTo) {
        for (i in positionFrom until positionTo) {
            val moved = this.move(from, i + 1)
            if (moved) {
                from = i + 1 - itemCount + 1
            }
        }
    } else {
        for (i in positionFrom downTo positionTo + 1) {
            val moved = this.move(from, i - 1)
            if (moved) {
                from = i - 1
            }
        }
    }
    this.onDragEnd()
}

fun ListItemSortedList.printList(text: String? = null) {
    text?.let { print("--------------\n$it\n") }
    println("--------------")
    println(toReadableString())
    println("--------------")
}

fun ListItemSortedList.find(vararg bodies: String): List<ListItem> {
    return bodies.map { body -> this.find { it.body == body }!! }
}

fun ListItemSortedList.assertOrder(vararg itemBodies: String) {
    itemBodies.forEachIndexed { position, s ->
        assertEquals("${this.toReadableString()}\nAt position: $position", s, get(position).body)
    }
}

fun ListItemSortedList.assertIds(vararg itemIds: Int) {
    itemIds.forEachIndexed { position, s -> assertEquals("id", s, get(position).id) }
}

fun ListItemSortedList.assertChecked(vararg checked: Boolean) {
    checked.forEachIndexed { index, expected ->
        assertEquals("checked at position: $index", expected, get(index).checked)
    }
}

fun ListItem.assertChildren(vararg childrenBodies: String) {
    assertFalse("isChild", this.isChild)
    if (childrenBodies.isNotEmpty()) {
        childrenBodies.forEachIndexed { index, s ->
            assertEquals("Child at position $index", s, children[index].body)
            assertTrue(children[index].isChild)
            assertTrue(children[index].children.isEmpty())
        }
    } else {
        assertTrue(
            "'${body}' expected empty children\t actual: ${
                children.joinToString(",") { "'${it.body}'" }
            } ",
            children.isEmpty(),
        )
    }
}

// fun ListMoveChange.assert(from: Int, itemsBeforeMove: List<ListItem>) {
//    assertEquals("from", from, position)
//    assertEquals("itemsBeforeMove", itemsBeforeMove, this.itemsBefore)
// }

// fun ListCheckedChange.assert(newValue: Boolean, itemId: Int) {
//    assertEquals("checked", newValue, this.newValue)
//    assertEquals("itemId", itemId, this.itemId)
// }

fun ListIsChildChange.assert(newValue: Boolean, position: Int) {
    assertEquals("isChild", newValue, this.newValue)
    assertEquals("position", position, this.position)
}

fun ListAddChange.assert(position: Int, newItem: ListItem) {
    assertEquals("position", position, this.position)
    assertEquals("newItem", newItem, this.itemBeforeInsert)
}

fun ListDeleteChange.assert(order: Int, deletedItem: ListItem?) {
    assertEquals("order", order, this.itemOrder)
    assertEquals("deletedItem", deletedItem, this.deletedItem)
}

// fun ChangeCheckedForAllChange.assert(checked: Boolean, changedPositions: Collection<Int>) {
//    assertEquals("checked", checked, this.checked)
//    assertEquals("changedIds", changedPositions, this.changedIds)
// }

// fun DeleteCheckedChange.assert(itemsBeforeDelete: List<ListItem>) {
//    assertEquals("itemsBeforeDelete", itemsBeforeDelete, this.deletedItems)
// }

object MockitoHelper {
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST") fun <T> uninitialized(): T = null as T
}
