package com.philkes.notallyx.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import com.philkes.notallyx.data.dao.NoteReminder
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.nextRepetition
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.presentation.activity.note.reminders.ReminderReceiver
import java.util.Date

private const val TAG = "ReminderExtensions"

fun Context.scheduleReminder(noteId: Long, reminder: Reminder, forceRepetition: Boolean = false) {
    val now = Date()
    if (forceRepetition || reminder.dateTime.before(now)) {
        reminder.repetition?.let {
            val nextRepetition = reminder.nextRepetition(now)!!
            Log.d(
                TAG,
                "scheduleReminder: noteId: $noteId reminderId: ${reminder.id} nextRepetition: ${nextRepetition.toText()}",
            )
            scheduleReminder(noteId, reminder.id, nextRepetition)
        }
    } else {
        Log.d(
            TAG,
            "scheduleReminder: noteId: $noteId reminderId: ${reminder.id} dateTime: ${reminder.dateTime.toText()}",
        )
        scheduleReminder(noteId, reminder.id, reminder.dateTime)
    }
}

fun Context.scheduleNoteReminders(noteReminders: List<NoteReminder>) {
    noteReminders.forEach { (noteId, reminders) ->
        reminders.forEach { reminder -> scheduleReminder(noteId, reminder) }
    }
}

fun Context.cancelNoteReminders(noteReminders: List<NoteReminder>) {
    noteReminders.forEach { (noteId, reminders) ->
        reminders.forEach { reminder -> cancelReminder(noteId, reminder.id) }
    }
}

@SuppressLint("ScheduleExactAlarm")
private fun Context.scheduleReminder(noteId: Long, reminderId: Long, dateTime: Date) {
    val pendingIntent = createReminderAlarmIntent(noteId, reminderId)
    val alarmManager = getSystemService<AlarmManager>()
    if (canScheduleAlarms()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dateTime.time,
                pendingIntent,
            )
        } else {
            alarmManager?.setExact(AlarmManager.RTC_WAKEUP, dateTime.time, pendingIntent)
        }
    }
}

fun Context.cancelReminder(noteId: Long, reminderId: Long) {
    Log.d(TAG, "cancelScheduledReminder: noteId: $noteId reminderId: $reminderId")
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = createReminderAlarmIntent(noteId, reminderId)
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()
}

private fun Context.createReminderAlarmIntent(noteId: Long, reminderId: Long): PendingIntent {
    val intent = Intent(this, ReminderReceiver::class.java)
    intent.putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
    intent.putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
    return PendingIntent.getBroadcast(
        this,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

fun Context.canScheduleAlarms() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    } else true
