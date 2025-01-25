package com.philkes.notallyx.presentation.activity.note.reminders

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.TimePicker
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.Repetition
import com.philkes.notallyx.data.model.RepetitionTimeUnit
import com.philkes.notallyx.data.model.toCalendar
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.databinding.ActivityRemindersBinding
import com.philkes.notallyx.databinding.DialogReminderCustomRepetitionBinding
import com.philkes.notallyx.databinding.DialogReminderRepetitionBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addCancelButton
import com.philkes.notallyx.presentation.checkAlarmPermission
import com.philkes.notallyx.presentation.checkNotificationPermission
import com.philkes.notallyx.presentation.initListView
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.presentation.view.main.reminder.ReminderAdapter
import com.philkes.notallyx.presentation.view.main.reminder.ReminderListener
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import com.philkes.notallyx.utils.canScheduleAlarms
import com.philkes.notallyx.utils.now
import java.util.Calendar
import kotlinx.coroutines.launch

class RemindersActivity : LockedActivity<ActivityRemindersBinding>(), ReminderListener {

    private lateinit var alarmPermissionActivityResultLauncher: ActivityResultLauncher<Intent>
    private val model: NotallyModel by viewModels()
    private lateinit var reminderAdapter: ReminderAdapter
    private var selectedReminder: Reminder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupRecyclerView()

        alarmPermissionActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (canScheduleAlarms()) {
                    showDatePickerDialog(selectedReminder)
                }
            }
        val noteId = intent.getLongExtra(NOTE_ID, 0L)
        lifecycleScope.launch {
            model.setState(noteId)
            if (model.reminders.value.isEmpty()) {
                showDatePickerDialog()
            } else if (!canScheduleAlarms()) {
                checkNotificationPermission(
                    REQUEST_NOTIFICATION_PERMISSION_REQUEST_CODE,
                    alsoCheckAlarmPermission = true,
                ) {}
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION_ON_OPEN_REQUEST_CODE -> {
                if (
                    grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    checkAlarmPermission(alarmPermissionActivityResultLauncher) {
                        showDatePickerDialog(selectedReminder)
                    }
                }
            }
        }
    }

    private fun setupToolbar() {
        binding.Toolbar.apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.add_reminder, R.drawable.add) { showDatePickerDialog() }
        }
    }

    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(this)
        binding.RecyclerView.apply {
            initListView(this@RemindersActivity)
            adapter = reminderAdapter
        }
        model.reminders.observe(this) { reminders ->
            reminderAdapter.submitList(reminders)
            if (reminders.isEmpty()) {
                binding.EmptyState.visibility = View.VISIBLE
            } else binding.EmptyState.visibility = View.INVISIBLE
        }
    }

    private fun showDatePickerDialog(reminder: Reminder? = null, calendar: Calendar? = null) {
        selectedReminder = reminder
        checkNotificationPermission(
            REQUEST_NOTIFICATION_PERMISSION_ON_OPEN_REQUEST_CODE,
            alsoCheckAlarmPermission = true,
            alarmPermissionResultLauncher = alarmPermissionActivityResultLauncher,
        ) {
            DatePickerFragment(calendar?.time ?: reminder?.dateTime) { _, year, month, day ->
                    val usedCalendar = calendar ?: reminder?.dateTime?.toCalendar() ?: now()
                    usedCalendar.set(year, month, day)
                    showTimePickerDialog(reminder, usedCalendar)
                }
                .show(supportFragmentManager, "reminderDatePicker")
        }
    }

    private fun showTimePickerDialog(reminder: Reminder? = null, calendar: Calendar) {
        TimePickerFragment(
                calendar,
                object : TimePickerListener {
                    override fun onBack() {
                        showDatePickerDialog(reminder, calendar)
                    }

                    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        showRepetitionDialog(reminder, calendar) { updatedRepetition ->
                            val updatedReminder =
                                Reminder(
                                    reminder?.id ?: NEW_REMINDER_ID,
                                    calendar.time,
                                    updatedRepetition,
                                )
                            if (reminder != null) {
                                lifecycleScope.launch { model.updateReminder(updatedReminder) }
                            } else {
                                lifecycleScope.launch { model.addReminder(updatedReminder) }
                            }
                        }
                    }
                },
            )
            .show(supportFragmentManager, "reminderTimePicker")
    }

    private fun showRepetitionDialog(
        reminder: Reminder? = null,
        calendar: Calendar,
        fromCustomRepetitionDialog: Boolean = false,
        onRepetitionSelected: (Repetition?) -> Unit,
    ) {
        val dialogView =
            DialogReminderRepetitionBinding.inflate(layoutInflater).apply {
                if (reminder == null && fromCustomRepetitionDialog) {
                    None.isChecked = true
                } else {
                    reminder?.repetition.apply {
                        when {
                            this == null -> None.isChecked = true
                            value == 1 && unit == RepetitionTimeUnit.DAYS -> Daily.isChecked = true

                            value == 1 && unit == RepetitionTimeUnit.WEEKS ->
                                Weekly.isChecked = true

                            value == 1 && unit == RepetitionTimeUnit.MONTHS ->
                                Monthly.isChecked = true

                            value == 1 && unit == RepetitionTimeUnit.YEARS ->
                                Yearly.isChecked = true

                            fromCustomRepetitionDialog -> Custom.isChecked = true
                            else -> {
                                showCustomRepetitionDialog(reminder, calendar, onRepetitionSelected)
                                return
                            }
                        }
                    }
                }
            }

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repetition)
                .setView(dialogView.root)
                .setPositiveButton(R.string.save) { _, _ ->
                    val repetition =
                        when (dialogView.RepetitionOptions.checkedRadioButtonId) {
                            R.id.None -> null
                            R.id.Daily -> Repetition(1, RepetitionTimeUnit.DAYS)
                            R.id.Weekly -> Repetition(1, RepetitionTimeUnit.WEEKS)
                            R.id.Monthly -> Repetition(1, RepetitionTimeUnit.MONTHS)
                            R.id.Yearly -> Repetition(1, RepetitionTimeUnit.YEARS)
                            R.id.Custom -> reminder?.repetition?.copy()
                            else -> null
                        }
                    onRepetitionSelected(repetition)
                }
                .setNegativeButton(R.string.back) { _, _ ->
                    showTimePickerDialog(reminder, calendar)
                }
                .show()
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        dialogView.apply {
            Custom.setOnClickListener {
                dialog.dismiss()
                showCustomRepetitionDialog(reminder, calendar, onRepetitionSelected)
            }
            None.setOnCheckedEnableButton(positiveButton)
            Daily.setOnCheckedEnableButton(positiveButton)
            Weekly.setOnCheckedEnableButton(positiveButton)
            Monthly.setOnCheckedEnableButton(positiveButton)
            Yearly.setOnCheckedEnableButton(positiveButton)
        }
    }

    private fun showCustomRepetitionDialog(
        reminder: Reminder? = null,
        calendar: Calendar,
        onRepetitionSelected: (Repetition?) -> Unit,
    ) {
        val dialogView =
            DialogReminderCustomRepetitionBinding.inflate(layoutInflater).apply {
                reminder?.repetition?.let {
                    when (it.unit) {
                        RepetitionTimeUnit.MINUTES -> Minutes
                        RepetitionTimeUnit.HOURS -> Hours
                        RepetitionTimeUnit.DAYS -> Days
                        RepetitionTimeUnit.WEEKS -> Weeks
                        RepetitionTimeUnit.MONTHS -> Months
                        RepetitionTimeUnit.YEARS -> Years
                    }.isChecked = true
                    Value.setText(it.value.toString())
                }
            }

        val dialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.repetition_custom)
                .setView(dialogView.root)
                .setPositiveButton(R.string.save) { _, _ ->
                    val value = dialogView.Value.text.toString().toIntOrNull() ?: 1
                    val selectedTimeUnit =
                        when (dialogView.TimeUnitGroup.checkedRadioButtonId) {
                            R.id.Minutes -> RepetitionTimeUnit.MINUTES
                            R.id.Hours -> RepetitionTimeUnit.HOURS
                            R.id.Days -> RepetitionTimeUnit.DAYS
                            R.id.Weeks -> RepetitionTimeUnit.WEEKS
                            R.id.Months -> RepetitionTimeUnit.MONTHS
                            R.id.Years -> RepetitionTimeUnit.YEARS
                            else -> null
                        }
                    onRepetitionSelected(selectedTimeUnit?.let { Repetition(value, it) })
                }
                .setBackgroundInsetBottom(0)
                .setBackgroundInsetTop(0)
                .setNegativeButton(R.string.back) { dialog, _ ->
                    dialog.dismiss()
                    showRepetitionDialog(
                        reminder,
                        calendar,
                        fromCustomRepetitionDialog = true,
                        onRepetitionSelected,
                    )
                }
                .showAndFocus(dialogView.Value)
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        dialogView.Value.doAfterTextChanged { text ->
            positiveButton.isEnabled = text.hasValueBiggerZero()
        }
        positiveButton.isEnabled = reminder?.repetition != null
    }

    private fun RadioButton.setOnCheckedEnableButton(button: Button) {
        setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                button.isEnabled = true
            }
        }
    }

    private fun Editable?.hasValueBiggerZero() =
        (!isNullOrEmpty() && toString().toIntOrNull()?.let { it > 0 } ?: false)

    private fun confirmDeletion(reminder: Reminder) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_reminder)
            .setMessage(
                "${reminder.dateTime.toText()}\n${reminder.repetition?.toText(this) ?: getString(R.string.reminder_no_repetition)}"
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch { model.removeReminder(reminder) }
            }
            .addCancelButton()
            .show()
    }

    override fun delete(reminder: Reminder) {
        confirmDeletion(reminder)
    }

    override fun edit(reminder: Reminder) {
        showDatePickerDialog(reminder)
    }

    companion object {
        const val NOTE_ID = "NOTE_ID"
        const val REQUEST_NOTIFICATION_PERMISSION_ON_OPEN_REQUEST_CODE = 101
        const val REQUEST_NOTIFICATION_PERMISSION_REQUEST_CODE = 102
        const val NEW_REMINDER_ID = -1L
    }
}
