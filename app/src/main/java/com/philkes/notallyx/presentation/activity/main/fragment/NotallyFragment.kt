package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.FragmentNotesBinding
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.misc.View as ViewPref
import com.philkes.notallyx.presentation.view.note.listitem.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel

abstract class NotallyFragment : Fragment(), ItemListener {

    private var adapter: BaseNoteAdapter? = null
    internal var binding: FragmentNotesBinding? = null

    internal val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        adapter = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.ImageView?.setImageResource(getBackground())

        setupAdapter()
        setupRecyclerView()
        setupObserver()
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
            adapter?.currentList?.get(position)?.let { item ->
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
            adapter?.currentList?.get(position)?.let { item ->
                if (item is BaseNote) {
                    handleNoteSelection(item.id, position, item)
                }
            }
        }
    }

    private fun handleNoteSelection(id: Long, position: Int, baseNote: BaseNote) {
        if (model.actionMode.selectedNotes.contains(id)) {
            model.actionMode.remove(id)
        } else model.actionMode.add(id, baseNote)
        adapter?.notifyItemChanged(position, 0)
    }

    private fun setupAdapter() {
        val textSize = model.preferences.textSize.value
        val maxItems = model.preferences.maxItems
        val maxLines = model.preferences.maxLines
        val maxTitle = model.preferences.maxTitle
        val dateFormat = model.preferences.dateFormat.value

        adapter =
            BaseNoteAdapter(
                model.actionMode.selectedIds,
                dateFormat,
                textSize,
                maxItems,
                maxLines,
                maxTitle,
                model.imageRoot,
                this,
            )
        adapter?.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    if (itemCount > 0) {
                        binding?.RecyclerView?.scrollToPosition(positionStart)
                    }
                }
            }
        )
        binding?.RecyclerView?.adapter = adapter
        binding?.RecyclerView?.setHasFixedSize(true)
    }

    private fun setupObserver() {
        getObservable().observe(viewLifecycleOwner) { list ->
            adapter?.submitList(list)
            binding?.ImageView?.isVisible = list.isEmpty()
        }

        model.actionMode.closeListener.observe(viewLifecycleOwner) { event ->
            event.handle { ids ->
                adapter?.currentList?.forEachIndexed { index, item ->
                    if (item is BaseNote && ids.contains(item.id)) {
                        adapter?.notifyItemChanged(index, 0)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding?.RecyclerView?.layoutManager =
            if (model.preferences.view.value == ViewPref.grid) {
                StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            } else LinearLayoutManager(requireContext())
    }

    private fun goToActivity(activity: Class<*>, baseNote: BaseNote) {
        val intent = Intent(requireContext(), activity)
        intent.putExtra(Constants.SelectedBaseNote, baseNote.id)
        startActivity(intent)
    }

    abstract fun getBackground(): Int

    abstract fun getObservable(): LiveData<List<Item>>
}
