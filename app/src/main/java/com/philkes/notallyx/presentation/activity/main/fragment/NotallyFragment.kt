package com.philkes.notallyx.presentation.activity.main.fragment

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.FragmentNotesBinding
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_FOLDER
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_LABEL
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_FOLDER_FROM
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_FOLDER_TO
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.hideKeyboard
import com.philkes.notallyx.presentation.movedToResId
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.main.BaseNoteVHPreferences
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotesView

abstract class NotallyFragment : Fragment(), ItemListener {

    private var notesAdapter: BaseNoteAdapter? = null
    private lateinit var openNoteActivityResultLauncher: ActivityResultLauncher<Intent>
    private var lastSelectedNotePosition = -1

    internal var binding: FragmentNotesBinding? = null

    internal val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        notesAdapter = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val layoutManager = binding?.MainListView?.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)
                val offset = firstVisibleView?.top ?: 0
                outState.putInt(EXTRA_SCROLL_POS, firstVisiblePosition)
                outState.putInt(EXTRA_SCROLL_OFFSET, offset)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.ImageView?.setImageResource(getBackground())

        setupAdapter()
        setupRecyclerView()
        setupObserver()
        setupSearch()

        setupActivityResultLaunchers()

        savedInstanceState?.let { bundle ->
            val scrollPosition = bundle.getInt(EXTRA_SCROLL_POS, -1)
            val scrollOffset = bundle.getInt(EXTRA_SCROLL_OFFSET, 0)
            if (scrollPosition > -1) {
                binding?.MainListView?.post {
                    val layoutManager = binding?.MainListView?.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(scrollPosition, scrollOffset)
                }
            }
        }
    }

    private fun setupActivityResultLaunchers() {
        openNoteActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    // If a note has been moved inside of EditActivity
                    // present snackbar to undo it
                    val data = result.data
                    val id = data?.getLongExtra(EXTRA_NOTE_ID, -1)
                    if (id != null) {
                        val folderFrom = Folder.valueOf(data.getStringExtra(EXTRA_FOLDER_FROM)!!)
                        val folderTo = Folder.valueOf(data.getStringExtra(EXTRA_FOLDER_TO)!!)
                        Snackbar.make(
                                binding!!.root,
                                requireContext().getQuantityString(folderTo.movedToResId(), 1),
                                Snackbar.LENGTH_SHORT,
                            )
                            .apply {
                                setAction(R.string.undo) {
                                    model.moveBaseNotes(longArrayOf(id), folderFrom)
                                }
                            }
                            .show()
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        setHasOptionsMenu(true)
        binding = FragmentNotesBinding.inflate(inflater)
        return binding?.root
    }

    // See [RecyclerView.ViewHolder.getAdapterPosition]
    override fun onClick(position: Int) {
        if (position != -1) {
            notesAdapter?.getItem(position)?.let { item ->
                if (item is BaseNote) {
                    if (model.actionMode.isEnabled()) {
                        handleNoteSelection(item.id, position, item)
                    } else {
                        when (item.type) {
                            Type.NOTE -> goToActivity(EditNoteActivity::class.java, item)
                            Type.LIST -> goToActivity(EditListActivity::class.java, item)
                        }
                    }
                }
            }
        }
    }

    override fun onLongClick(position: Int) {
        if (position != -1) {
            if (model.actionMode.selectedNotes.isNotEmpty()) {
                if (lastSelectedNotePosition > position) {
                        position..lastSelectedNotePosition
                    } else {
                        lastSelectedNotePosition..position
                    }
                    .forEach { pos ->
                        notesAdapter!!.getItem(pos)?.let { item ->
                            if (item is BaseNote) {
                                if (!model.actionMode.selectedNotes.contains(item.id)) {
                                    handleNoteSelection(item.id, pos, item)
                                }
                            }
                        }
                    }
            } else {
                notesAdapter?.getItem(position)?.let { item ->
                    if (item is BaseNote) {
                        handleNoteSelection(item.id, position, item)
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        binding?.EnterSearchKeyword?.apply {
            setText(model.keyword)
            val navController = findNavController()
            navController.addOnDestinationChangedListener { controller, destination, arguments ->
                if (destination.id == R.id.Search) {
                    //                setText("")
                    visibility = View.VISIBLE
                    requestFocus()
                    activity?.showKeyboard(this)
                } else {
                    //                visibility = View.GONE
                    setText("")
                    clearFocus()
                    activity?.hideKeyboard(this)
                }
            }
            doAfterTextChanged { text ->
                val isSearchFragment = navController.currentDestination?.id == R.id.Search
                if (isSearchFragment) {
                    model.keyword = requireNotNull(text).trim().toString()
                }
                if (text?.isNotEmpty() == true && !isSearchFragment) {
                    setText("")
                    model.keyword = text.trim().toString()
                    navController.navigate(
                        R.id.Search,
                        Bundle().apply {
                            putSerializable(EXTRA_INITIAL_FOLDER, model.folder.value)
                            putSerializable(EXTRA_INITIAL_LABEL, model.currentLabel)
                        },
                    )
                }
            }
        }
    }

    private fun handleNoteSelection(id: Long, position: Int, baseNote: BaseNote) {
        if (model.actionMode.selectedNotes.contains(id)) {
            model.actionMode.remove(id)
        } else {
            model.actionMode.add(id, baseNote)
            lastSelectedNotePosition = position
        }
        notesAdapter?.notifyItemChanged(position, 0)
    }

    private fun setupAdapter() {
        notesAdapter =
            with(model.preferences) {
                BaseNoteAdapter(
                    model.actionMode.selectedIds,
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
                    model.imageRoot,
                    this@NotallyFragment,
                )
            }

        notesAdapter?.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        binding?.MainListView?.scrollToPosition(positionStart)
                    }
                }
            }
        )
        binding?.MainListView?.apply {
            adapter = notesAdapter
            setHasFixedSize(false)
        }
        model.actionMode.addListener = { notesAdapter?.notifyDataSetChanged() }
        if (activity is MainActivity) {
            (activity as MainActivity).getCurrentFragmentNotes = {
                notesAdapter?.currentList?.filterIsInstance<BaseNote>()
            }
        }
    }

    private fun setupObserver() {
        getObservable().observe(viewLifecycleOwner) { list ->
            notesAdapter?.submitList(list)
            binding?.ImageView?.isVisible = list.isEmpty()
        }

        model.preferences.notesSorting.observe(viewLifecycleOwner) { notesSort ->
            notesAdapter?.setNotesSort(notesSort)
        }

        model.actionMode.closeListener.observe(viewLifecycleOwner) { event ->
            event.handle { ids ->
                notesAdapter?.currentList?.forEachIndexed { index, item ->
                    if (item is BaseNote && ids.contains(item.id)) {
                        notesAdapter?.notifyItemChanged(index, 0)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding?.MainListView?.layoutManager =
            if (model.preferences.notesView.value == NotesView.GRID) {
                StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            } else LinearLayoutManager(requireContext())
    }

    private fun goToActivity(activity: Class<*>, baseNote: BaseNote) {
        val intent = Intent(requireContext(), activity)
        intent.putExtra(EXTRA_SELECTED_BASE_NOTE, baseNote.id)
        openNoteActivityResultLauncher.launch(intent)
    }

    abstract fun getBackground(): Int

    abstract fun getObservable(): LiveData<List<Item>>

    open fun prepareNewNoteIntent(intent: Intent): Intent {
        return intent
    }

    companion object {
        private const val EXTRA_SCROLL_POS = "notallyx.intent.extra.SCROLL_POS"
        private const val EXTRA_SCROLL_OFFSET = "notallyx.intent.extra.SCROLL_OFFSET"
    }
}
