package com.philkes.notallyx.presentation.activity.note.reminders

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.philkes.notallyx.utils.now
import java.util.Calendar
import java.util.Date

class DatePickerFragment(
    private val date: Date?,
    private val listener: DatePickerDialog.OnDateSetListener,
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val now = now()
        val c = date?.let { Calendar.getInstance().apply { time = it } } ?: now
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        return DatePickerDialog(requireContext(), listener, year, month, day)
    }
}
