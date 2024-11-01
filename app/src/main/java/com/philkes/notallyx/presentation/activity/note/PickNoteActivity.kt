package com.philkes.notallyx.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Header
import com.philkes.notallyx.databinding.ActivityPickNoteBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView
import com.philkes.notallyx.utils.IO.getExternalImagesDirectory
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class PickNoteActivity : LockedActivity<ActivityPickNoteBinding>(), ListItemListener {

    protected lateinit var adapter: BaseNoteAdapter

    private val excludedNoteId by lazy { intent.getLongExtra(EXCLUDE_NOTE_ID, -1L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = Intent()
        setResult(RESULT_CANCELED, result)

        val preferences = NotallyXPreferences.getInstance(application)

        adapter =
            with(preferences) {
                BaseNoteAdapter(
                    Collections.emptySet(),
                    dateFormat.value,
                    notesSorting.value.sortedBy,
                    textSize.value,
                    maxItems.value,
                    maxLines.value,
                    maxTitle.value,
                    application.getExternalImagesDirectory(),
                    this@PickNoteActivity,
                )
            }

        binding.RecyclerView.apply {
            adapter = this@PickNoteActivity.adapter
            setHasFixedSize(true)
            layoutManager =
                if (preferences.notesView.value == NotesView.GRID) {
                    StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
                } else LinearLayoutManager(this@PickNoteActivity)
        }

        val database = NotallyDatabase.getDatabase(application)

        val pinned = Header(getString(R.string.pinned))
        val others = Header(getString(R.string.others))

        database.observe(this) {
            lifecycleScope.launch {
                val notes =
                    withContext(Dispatchers.IO) {
                        val raw =
                            it.getBaseNoteDao().getAllNotes().filter { it.id != excludedNoteId }
                        BaseNoteModel.transform(raw, pinned, others)
                    }
                adapter.submitList(notes)
                binding.EmptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onClick(position: Int) {
        if (position != -1) {
            val note = (adapter.getItem(position) as BaseNote)
            val success = Intent()
            success.putExtra(PICKED_NOTE_ID, note.id)
            success.putExtra(PICKED_NOTE_TITLE, note.title)
            success.putExtra(PICKED_NOTE_TYPE, note.type.name)
            setResult(RESULT_OK, success)
            finish()
        }
    }

    override fun onLongClick(position: Int) {}

    companion object {
        const val EXCLUDE_NOTE_ID = "EXCLUDE_NOTE_ID"

        const val PICKED_NOTE_ID = "PICKED_NOTE_ID"
        const val PICKED_NOTE_TITLE = "PICKED_NOTE_TITLE"
        const val PICKED_NOTE_TYPE = "PICKED_NOTE_TYPE"
    }
}
