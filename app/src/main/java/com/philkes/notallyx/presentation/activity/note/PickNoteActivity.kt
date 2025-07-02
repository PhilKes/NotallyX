package com.philkes.notallyx.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.philkes.notallyx.presentation.view.main.BaseNoteVHPreferences
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView
import com.philkes.notallyx.utils.getExternalImagesDirectory
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class PickNoteActivity : LockedActivity<ActivityPickNoteBinding>(), ItemListener {

    protected lateinit var adapter: BaseNoteAdapter

    private val excludedNoteId by lazy { intent.getLongExtra(EXTRA_EXCLUDE_NOTE_ID, -1L) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        val result = Intent()
        setResult(RESULT_CANCELED, result)

        val preferences = NotallyXPreferences.getInstance(application)

        adapter =
            with(preferences) {
                BaseNoteAdapter(
                    Collections.emptySet(),
                    dateFormat.value,
                    notesSorting.value,
                    BaseNoteVHPreferences(
                        textSize.value,
                        maxItems.value,
                        maxLines.value,
                        maxTitle.value,
                        labelTagsHiddenInOverview.value,
                        imagesHiddenInOverview.value,
                    ),
                    application.getExternalImagesDirectory(),
                    this@PickNoteActivity,
                )
            }

        binding.MainListView.apply {
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
        val archived = Header(getString(R.string.archived))

        database.observe(this) {
            lifecycleScope.launch {
                val notes =
                    withContext(Dispatchers.IO) {
                        val raw =
                            it.getBaseNoteDao().getAllNotes().filter { it.id != excludedNoteId }
                        BaseNoteModel.transform(raw, pinned, others, archived)
                    }
                adapter.submitList(notes)
                binding.EmptyView.visibility =
                    if (notes.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        // 1. Enable edge-to-edge display for the activity window.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 3. Apply window insets to specific views to prevent content from being obscured.
        // Set an OnApplyWindowInsetsListener on the root layout.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Adjust the top margin of the Toolbar to account for the status bar.
            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout()
            }

            // Apply padding to the RecyclerView and EmptyView to account for the navigation bar and
            // keyboard.
            // Preserve existing horizontal padding.
            binding.MainListView.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    systemBarsInsets.bottom + imeInsets.bottom,
                )
            }
            binding.EmptyView.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    systemBarsInsets.bottom + imeInsets.bottom,
                )
            }
            insets
        }
    }

    override fun onClick(position: Int) {
        if (position != -1) {
            val note = (adapter.getItem(position) as BaseNote)
            val success = Intent()
            success.putExtra(EXTRA_PICKED_NOTE_ID, note.id)
            success.putExtra(EXTRA_PICKED_NOTE_TITLE, note.title)
            success.putExtra(EXTRA_PICKED_NOTE_TYPE, note.type.name)
            setResult(RESULT_OK, success)
            finish()
        }
    }

    override fun onLongClick(position: Int) {}

    companion object {
        const val EXTRA_EXCLUDE_NOTE_ID = "notallyx.intent.extra.EXCLUDE_NOTE_ID"

        const val EXTRA_PICKED_NOTE_ID = "notallyx.intent.extra.PICKED_NOTE_ID"
        const val EXTRA_PICKED_NOTE_TITLE = "notallyx.intent.extra.PICKED_NOTE_TITLE"
        const val EXTRA_PICKED_NOTE_TYPE = "notallyx.intent.extra.PICKED_NOTE_TYPE"
    }
}
