package com.philkes.notallyx.presentation.activity.note.reminders

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.format.DateFormat
import androidx.fragment.app.DialogFragment
import com.philkes.notallyx.R
import java.util.Calendar

class TimePickerFragment(private val calendar: Calendar, private val listener: TimePickerListener) :
    DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val dialog =
            TimePickerDialog(activity, listener, hour, minute, DateFormat.is24HourFormat(activity))
        dialog.setButton(
            DialogInterface.BUTTON_NEGATIVE,
            requireContext().getText(R.string.back),
        ) { _, _ ->
            listener.onBack()
        }
        return dialog
    }
}

interface TimePickerListener : TimePickerDialog.OnTimeSetListener {
    fun onBack()
}
