package com.philkes.notallyx.changehistory

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.philkes.notallyx.test.mockAndroidLog
import com.philkes.notallyx.utils.changehistory.Change
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ChangeHistoryTest {
    private lateinit var changeHistory: ChangeHistory

    @get:Rule val rule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        mockAndroidLog()
        changeHistory = ChangeHistory()
    }

    @Test
    fun `test push adds change to stack and updates stackPointer`() {
        val change = mock<Change>()

        changeHistory.push(change)

        assertTrue(changeHistory.canUndo.value)
    }

    @Test
    fun `test undo when stack has one change`() {
        val change = mock<Change>()

        changeHistory.push(change)
        changeHistory.undo()

        verify(change).undo()
        assertFalse(changeHistory.canUndo.value)
        assertTrue(changeHistory.canRedo.value)
    }

    @Test
    fun `test redo when stack has one change`() {
        val change = mock<Change>()

        changeHistory.push(change)
        changeHistory.undo()
        changeHistory.redo()

        verify(change).redo()
        assertTrue(changeHistory.canUndo.value)
        assertFalse(changeHistory.canRedo.value)
    }

    @Test
    fun `test canUndo and canRedo logic`() {
        val change = mock<Change>()

        assertFalse(changeHistory.canUndo.value)
        assertFalse(changeHistory.canRedo.value)

        changeHistory.push(change)

        assertTrue(changeHistory.canUndo.value)
        assertFalse(changeHistory.canRedo.value)

        changeHistory.undo()

        assertFalse(changeHistory.canUndo.value)
        assertTrue(changeHistory.canRedo.value)
    }

    @Test
    fun `test invalidateRedos`() {
        val change1 = TestChange()
        val change2 = TestChange()
        val change3 = TestChange()
        val change4 = TestChange()

        changeHistory.push(change1)
        changeHistory.push(change2)
        changeHistory.push(change3)
        changeHistory.undo()
        changeHistory.push(change4)

        assertEquals(change4, changeHistory.lookUp())
        assertEquals(change2, changeHistory.lookUp(1))
        assertEquals(change1, changeHistory.lookUp(2))
        assertThrows(IllegalArgumentException::class.java) { changeHistory.lookUp(3) }
    }

    class TestChange : Change {
        override fun redo() {}

        override fun undo() {}
    }
}
