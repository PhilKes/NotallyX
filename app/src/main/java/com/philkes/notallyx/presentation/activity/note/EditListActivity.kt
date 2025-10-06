package com.philkes.notallyx.presentation.activity.note

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.hideKeyboardOnFocusedItem
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showKeyboardOnFocusedItem
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.action.MoreListBottomSheet
import com.philkes.notallyx.presentation.view.note.listitem.HighlightText
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.adapter.CheckedListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemHighlight
import com.philkes.notallyx.presentation.view.note.listitem.adapter.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.init
import com.philkes.notallyx.presentation.view.note.listitem.setItems
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemParentSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.SortedItemsList
import com.philkes.notallyx.presentation.view.note.listitem.splitByChecked
import com.philkes.notallyx.presentation.view.note.listitem.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.autoSortByCheckedEnabled
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.indices
import com.philkes.notallyx.utils.mapIndexed
import java.util.concurrent.atomic.AtomicInteger

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private var adapterChecked: CheckedListItemAdapter? = null
    private val items: MutableList<ListItem>
        get() = adapter!!.items

    private var itemsChecked: SortedItemsList? = null
    private lateinit var listManager: ListManager

    override fun finish() {
        notallyModel.setItems(items.toMutableList() + (itemsChecked?.toMutableList() ?: listOf()))
        super.finish()
    }

    override fun updateModel() {
        super.updateModel()
        notallyModel.setItems(items.toMutableList() + (itemsChecked?.toMutableList() ?: listOf()))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        updateModel()
        binding.MainListView.focusedChild?.let { focusedChild ->
            val viewHolder = binding.MainListView.findContainingViewHolder(focusedChild)
            if (viewHolder is ListItemVH) {
                val itemPos = binding.MainListView.getChildAdapterPosition(focusedChild)
                if (itemPos > -1) {
                    val (selectionStart, selectionEnd) = viewHolder.getSelection()
                    outState.apply {
                        putInt(EXTRA_ITEM_POS, itemPos)
                        putInt(EXTRA_SELECTION_START, selectionStart)
                        putInt(EXTRA_SELECTION_END, selectionEnd)
                    }
                }
            }
        }

        super.onSaveInstanceState(outState)
    }

    override fun toggleCanEdit(mode: NoteViewMode) {
        super.toggleCanEdit(mode)
        when (mode) {
            NoteViewMode.EDIT -> binding.MainListView.showKeyboardOnFocusedItem()
            NoteViewMode.READ_ONLY -> binding.MainListView.hideKeyboardOnFocusedItem()
        }
        adapter?.viewMode = mode
        adapterChecked?.viewMode = mode
        binding.AddItem.visibility =
            when (mode) {
                NoteViewMode.EDIT -> View.VISIBLE
                else -> View.GONE
            }
    }

    override fun deleteChecked() {
        listManager.deleteCheckedItems()
    }

    override fun checkAll() {
        listManager.changeCheckedForAll(true)
    }

    override fun uncheckAll() {
        listManager.changeCheckedForAll(false)
    }

    override fun initBottomMenu() {
        super.initBottomMenu()
        binding.BottomAppBarRight.apply {
            removeAllViews()
            addToggleViewMode()
            addIconButton(R.string.tap_for_more_options, R.drawable.more_vert, marginStart = 0) {
                MoreListBottomSheet(
                        this@EditListActivity,
                        createNoteTypeActions() + createFolderActions(),
                        colorInt,
                    )
                    .show(supportFragmentManager, MoreListBottomSheet.TAG)
            }
        }
        setBottomAppBarColor(colorInt)
    }

    private fun SortedList<ListItem>.highlightSearch(
        search: String,
        adapter: HighlightText?,
        resultPosCounter: AtomicInteger,
        alreadyNotifiedItemPos: MutableSet<Int>,
    ): Int {
        return mapIndexed { idx, item ->
                val occurrences = item.body.findAllOccurrences(search)
                occurrences.onEach { (startIdx, endIdx) ->
                    adapter?.highlightText(
                        ListItemHighlight(
                            idx,
                            resultPosCounter.getAndIncrement(),
                            startIdx,
                            endIdx,
                            false,
                        )
                    )
                }
                if (occurrences.isNotEmpty()) {
                    alreadyNotifiedItemPos.add(idx)
                }
                occurrences.size
            }
            .sum()
    }

    private fun List<ListItem>.highlightSearch(
        search: String,
        adapter: ListItemAdapter?,
        resultPosCounter: AtomicInteger,
        alreadyNotifiedItemPos: MutableSet<Int>,
    ): Int {
        return mapIndexed { idx, item ->
                val occurrences = item.body.findAllOccurrences(search)
                occurrences.onEach { (startIdx, endIdx) ->
                    adapter?.highlightText(
                        ListItemHighlight(
                            idx,
                            resultPosCounter.getAndIncrement(),
                            startIdx,
                            endIdx,
                            false,
                        )
                    )
                }
                if (occurrences.isNotEmpty()) {
                    alreadyNotifiedItemPos.add(idx)
                }
                occurrences.size
            }
            .sum()
    }

    override fun highlightSearchResults(search: String): Int {
        val resultPosCounter = AtomicInteger(0)
        val alreadyNotifiedItemPos = mutableSetOf<Int>()
        adapter?.clearHighlights()
        adapterChecked?.clearHighlights()
        val amount =
            items.highlightSearch(search, adapter, resultPosCounter, alreadyNotifiedItemPos) +
                (itemsChecked?.highlightSearch(
                    search,
                    adapterChecked,
                    resultPosCounter,
                    alreadyNotifiedItemPos,
                ) ?: 0)
        items.indices
            .filter { !alreadyNotifiedItemPos.contains(it) }
            .forEach { adapter?.notifyItemChanged(it) }
        itemsChecked
            ?.indices
            ?.filter { !alreadyNotifiedItemPos.contains(it) }
            ?.forEach { adapter?.notifyItemChanged(it) }
        return amount
    }

    override fun selectSearchResult(resultPos: Int) {
        var selectedItemPos = adapter!!.selectHighlight(resultPos)
        if (selectedItemPos == -1 && adapterChecked != null) {
            selectedItemPos = adapterChecked!!.selectHighlight(resultPos)
            if (selectedItemPos != -1) {
                binding.CheckedListView.scrollToItemPosition(selectedItemPos)
            }
        } else if (selectedItemPos != -1) {
            binding.MainListView.scrollToItemPosition(selectedItemPos)
        }
    }

    private fun RecyclerView.scrollToItemPosition(position: Int) {
        post {
            findViewHolderForAdapterPosition(position)?.itemView?.let {
                binding.ScrollView.scrollTo(0, top + it.top)
            }
        }
    }

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { listManager.moveFocusToNext(-1) }

        if (notallyModel.items.isEmpty()) {
            listManager.add(pushChange = false)
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.AddItem.setOnClickListener { listManager.add() }
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        val elevation = resources.displayMetrics.density * 2
        listManager =
            ListManager(
                binding.MainListView,
                changeHistory,
                preferences,
                inputMethodManager,
                {
                    if (isInSearchMode()) {
                        endSearch()
                    }
                },
            ) { _ ->
                if (isInSearchMode() && search.results.value > 0) {
                    updateSearchResults(search.query)
                }
            }
        adapter =
            ListItemAdapter(
                colorInt,
                notallyModel.textSize,
                elevation,
                NotallyXPreferences.getInstance(application),
                listManager,
                false,
                binding.ScrollView,
            )
        val initializedItems = notallyModel.items.init(true)
        if (preferences.autoSortByCheckedEnabled) {
            val (checkedItems, uncheckedItems) = initializedItems.splitByChecked()
            adapter?.submitList(uncheckedItems.toMutableList())
            adapterChecked =
                CheckedListItemAdapter(
                    colorInt,
                    notallyModel.textSize,
                    elevation,
                    NotallyXPreferences.getInstance(application),
                    listManager,
                    true,
                    binding.ScrollView,
                )
            itemsChecked =
                SortedItemsList(ListItemParentSortCallback(adapterChecked!!)).apply {
                    setItems(checkedItems.toMutableList())
                }
            adapterChecked?.setList(itemsChecked!!)
            binding.CheckedListView.adapter = adapterChecked
        } else {
            adapter?.submitList(initializedItems.toMutableList())
        }
        listManager.init(adapter!!, itemsChecked, adapterChecked)
        binding.MainListView.adapter = adapter

        savedInstanceState?.let {
            val itemPos = it.getInt(EXTRA_ITEM_POS, -1)
            if (itemPos > -1) {
                binding.MainListView.apply {
                    post {
                        scrollToPosition(itemPos)
                        val viewHolder = findViewHolderForLayoutPosition(itemPos)
                        if (viewHolder is ListItemVH) {
                            val selectionStart = it.getInt(EXTRA_SELECTION_START, -1)
                            val selectionEnd = it.getInt(EXTRA_SELECTION_END, -1)
                            viewHolder.focusEditText(
                                selectionStart,
                                selectionEnd,
                                inputMethodManager,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun setColor() {
        super.setColor()
        adapter?.setBackgroundColor(colorInt)
        adapterChecked?.setBackgroundColor(colorInt)
    }

    companion object {
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
