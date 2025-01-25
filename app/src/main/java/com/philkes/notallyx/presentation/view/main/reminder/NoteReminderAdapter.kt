package com.philkes.notallyx.presentation.view.main.reminder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.data.dao.NoteReminder
import com.philkes.notallyx.databinding.RecyclerNoteReminderBinding

class NoteReminderAdapter(private val listener: NoteReminderListener) :
    ListAdapter<NoteReminder, NoteReminderVH>(NoteReminderDiffCallback()) {

    override fun onBindViewHolder(holder: NoteReminderVH, position: Int) {
        val reminder = getItem(position)
        holder.bind(reminder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteReminderVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerNoteReminderBinding.inflate(inflater, parent, false)
        return NoteReminderVH(binding, listener)
    }
}

interface NoteReminderListener {
    fun openReminder(reminder: NoteReminder)

    fun openNote(reminder: NoteReminder)
}

class NoteReminderDiffCallback : DiffUtil.ItemCallback<NoteReminder>() {

    override fun areItemsTheSame(oldItem: NoteReminder, newItem: NoteReminder): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: NoteReminder, newItem: NoteReminder): Boolean {
        return oldItem == newItem
    }
}
