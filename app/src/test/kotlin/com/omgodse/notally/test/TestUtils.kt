package com.omgodse.notally.test

import android.util.Log
import com.omgodse.notally.changehistory.ChangeCheckedForAllChange
import com.omgodse.notally.changehistory.DeleteCheckedChange
import com.omgodse.notally.changehistory.ListAddChange
import com.omgodse.notally.changehistory.ListCheckedChange
import com.omgodse.notally.changehistory.ListDeleteChange
import com.omgodse.notally.changehistory.ListIsChildChange
import com.omgodse.notally.changehistory.ListMoveChange
import com.omgodse.notally.recyclerview.ListItemSortedList
import com.omgodse.notally.recyclerview.ListManager
import com.omgodse.notally.recyclerview.toReadableString
import com.omgodse.notally.room.ListItem
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.Mockito

fun createListItem(
    body: String,
    checked: Boolean = false,
    isChild: Boolean = false,
    sortingPosition: Int? = null,
    children: MutableList<ListItem> = mutableListOf(),
    id: Int = -1,
): ListItem {
    return ListItem(body, checked, isChild, sortingPosition, children, id)
}

fun mockAndroidLog() {
    mockkStatic(Log::class)
    every { Log.v(any(), any()) } returns 0
    every { Log.d(any(), any()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
}

fun ListManager.simulateDrag(positionFrom: Int, positionTo: Int): Int? {
    val item = this.getItem(positionFrom).clone() as ListItem
    val itemCount = item.children.size + 1
    var newPosition: Int? = positionTo
    if (positionFrom < positionTo) {
        for (i in positionFrom..positionTo - itemCount) {
            newPosition = this.move(i, i + itemCount, false, false)
        }
    } else {
        for (i in positionFrom downTo positionTo + 1) {
            newPosition = this.move(i, i - 1, false, false)
        }
    }
    if (newPosition != null) {
        this.finishMove(positionFrom, positionTo, newPosition, item, true, true)
    }
    return newPosition
}

fun ListItemSortedList.printList(text: String? = null) {
    text?.let { print("--------------\n$it\n") }
    println("--------------")
    println(toReadableString())
    println("--------------")
}

fun ListItemSortedList.assertOrder(vararg itemBodies: String) {
    itemBodies.forEachIndexed { position, s ->
        assertEquals("${this.toReadableString()}\nAt position: $position", s, get(position).body)
    }
}

fun ListItemSortedList.assertChecked(vararg checked: Boolean) {
    checked.forEachIndexed { index, expected ->
        assertEquals("checked at position: $index", expected, get(index).checked)
    }
}

fun ListItem.assertChildren(vararg childrenBodies: String) {
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

fun ListMoveChange.assert(from: Int, to: Int, after: Int, itemBeforeMove: String) {
    assertEquals("from", from, position)
    assertEquals("to", to, positionTo)
    assertEquals("after", after, positionAfter)
    assertEquals("itemBeforeMove", itemBeforeMove, this.itemBeforeMove.toString())
}

fun ListCheckedChange.assert(newValue: Boolean, position: Int, positionAfter: Int) {
    assertEquals("checked", newValue, this.newValue)
    assertEquals("position", position, this.position)
    assertEquals("positionAfter", positionAfter, this.positionAfter)
}

fun ListIsChildChange.assert(newValue: Boolean, position: Int, positionAfter: Int) {
    assertEquals("isChild", newValue, this.newValue)
    assertEquals("position", position, this.position)
    assertEquals("positionAfter", positionAfter, this.positionAfter)
}

fun ListAddChange.assert(position: Int, newItem: ListItem) {
    assertEquals("position", position, this.position)
    assertEquals("newItem", newItem, this.itemBeforeInsert)
}

fun ListDeleteChange.assert(position: Int, deletedItem: ListItem?) {
    assertEquals("position", position, this.position)
    assertEquals("deletedItem", deletedItem, this.deletedItem)
}

fun ChangeCheckedForAllChange.assert(
    checked: Boolean,
    changedPositions: Collection<Int>
) {
    assertEquals("checked", checked, this.checked)
    assertEquals("changedIds", changedPositions, this.changedIds)
}

fun DeleteCheckedChange.assert(itemsBeforeDelete: List<ListItem>) {
    assertEquals("itemsBeforeDelete", itemsBeforeDelete, this.itemsBeforeDelete)
}

object MockitoHelper {
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST") fun <T> uninitialized(): T = null as T
}
