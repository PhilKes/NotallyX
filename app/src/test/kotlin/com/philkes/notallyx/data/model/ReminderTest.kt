package com.philkes.notallyx.data.model

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTest {

    @Test
    fun testToMillis() {
        assertEquals(60 * 1000, Repetition(1, RepetitionTimeUnit.MINUTES).toMillis())
        assertEquals(60 * 60 * 1000, Repetition(1, RepetitionTimeUnit.HOURS).toMillis())
        assertEquals(24 * 60 * 60 * 1000, Repetition(1, RepetitionTimeUnit.DAYS).toMillis())
        assertEquals(7L * 24 * 60 * 60 * 1000, Repetition(1, RepetitionTimeUnit.WEEKS).toMillis())
        assertEquals(31L * 24 * 60 * 60 * 1000, Repetition(1, RepetitionTimeUnit.MONTHS).toMillis())
        assertEquals(365L * 24 * 60 * 60 * 1000, Repetition(1, RepetitionTimeUnit.YEARS).toMillis())
    }

    @Test
    fun testNextRepetition() {
        val repetitionStart = Calendar.getInstance().apply { set(2000, 0, 1, 0, 0, 0) }
        val reminder = Reminder(0, repetitionStart.time, Repetition(1, RepetitionTimeUnit.YEARS))
        val from = Calendar.getInstance().apply { set(2004, 6, 3, 3, 1, 2) }.time

        val actual = reminder.nextRepetition(from)!!.time

        // Expected: 01.01.2005
        val expected = repetitionStart.copy().apply { add(Calendar.YEAR, 5) }.timeInMillis
        assertEquals(expected, actual)
    }

    private fun Calendar.copy(): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeInMillis
        return calendar
    }
}
