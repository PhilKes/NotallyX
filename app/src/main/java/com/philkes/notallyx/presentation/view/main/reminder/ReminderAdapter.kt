package com.philkes.notallyx.presentation.view.main.reminder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.databinding.RecyclerReminderBinding

class ReminderAdapter(private val listener: ReminderListener) :
    ListAdapter<Reminder, ReminderVH>(ReminderDiffCallback()) {

    override fun onBindViewHolder(holder: ReminderVH, position: Int) {
        val reminder = getItem(position)
        holder.bind(reminder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerReminderBinding.inflate(inflater, parent, false)
        return ReminderVH(binding, listener)
    }
}

interface ReminderListener {
    fun delete(reminder: Reminder)

    fun edit(reminder: Reminder)
}

class ReminderDiffCallback : DiffUtil.ItemCallback<Reminder>() {

    override fun areItemsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Reminder, newItem: Reminder): Boolean {
        return oldItem == newItem
    }
}
