package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.philkes.notallyx.R
import com.philkes.notallyx.data.dao.NoteReminder
import com.philkes.notallyx.data.model.hasAnyUpcomingNotifications
import com.philkes.notallyx.databinding.FragmentRemindersBinding
import com.philkes.notallyx.presentation.activity.note.reminders.RemindersActivity
import com.philkes.notallyx.presentation.initListView
import com.philkes.notallyx.presentation.view.main.reminder.NoteReminderAdapter
import com.philkes.notallyx.presentation.view.main.reminder.NoteReminderListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.utils.getOpenNoteIntent

class RemindersFragment : Fragment(), NoteReminderListener {

    private var reminderAdapter: NoteReminderAdapter? = null
    private var binding: FragmentRemindersBinding? = null
    private lateinit var allReminders: List<NoteReminder>

    private val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        reminderAdapter = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        reminderAdapter = NoteReminderAdapter(this)

        binding?.RecyclerView?.apply {
            initListView(requireContext())
            adapter = reminderAdapter
            binding?.ImageView?.setImageResource(R.drawable.notifications)
        }
        binding?.ChipGroup?.setOnCheckedStateChangeListener { _, _ -> updateList() }

        model.reminders.observe(viewLifecycleOwner) { reminders ->
            allReminders = reminders.sortedBy { it.title }
            updateList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        setHasOptionsMenu(true)
        binding = FragmentRemindersBinding.inflate(inflater)
        return binding?.root
    }

    private fun updateList() {
        val list =
            when (binding?.ChipGroup?.checkedChipId) {
                R.id.Upcoming -> allReminders.filter { it.reminders.hasAnyUpcomingNotifications() }
                R.id.Past -> allReminders.filter { !it.reminders.hasAnyUpcomingNotifications() }
                else -> allReminders
            }
        reminderAdapter?.submitList(list)
        binding?.ImageView?.isVisible = list.isEmpty()
    }

    override fun openReminder(reminder: NoteReminder) {
        val intent =
            Intent(requireContext(), RemindersActivity::class.java).apply {
                putExtra(RemindersActivity.NOTE_ID, reminder.id)
            }
        startActivity(intent)
    }

    override fun openNote(reminder: NoteReminder) {
        startActivity(requireContext().getOpenNoteIntent(reminder.id, reminder.type))
    }
}
