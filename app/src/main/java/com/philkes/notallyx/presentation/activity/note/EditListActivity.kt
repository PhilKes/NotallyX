package com.philkes.notallyx.presentation.activity.note

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.action.MoreListBottomSheet
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListItemVH
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.indices
import com.philkes.notallyx.presentation.view.note.listitem.sorting.init
import com.philkes.notallyx.presentation.view.note.listitem.sorting.mapIndexed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.splitByChecked
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.findAllOccurrences
import java.util.concurrent.atomic.AtomicInteger

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private var adapterChecked: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList
    private var itemsChecked: ListItemSortedList? = null
    private lateinit var listManager: ListManager

    override fun finish() {
        updateModel()
        super.finish()
    }

    private fun updateModel() {
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
            addIconButton(R.string.more, R.drawable.more_vert, marginStart = 0) {
                MoreListBottomSheet(this@EditListActivity, createFolderActions(), colorInt)
                    .show(supportFragmentManager, MoreListBottomSheet.TAG)
            }
        }
        setBottomAppBarColor(colorInt)
    }

    private fun ListItemSortedList.highlightSearch(
        search: String,
        adapter: ListItemAdapter?,
        resultPosCounter: AtomicInteger,
        alreadyNotifiedItemPos: MutableSet<Int>,
    ): Int {
        return mapIndexed { idx, item ->
                val occurrences = item.body.findAllOccurrences(search)
                occurrences.onEach { (startIdx, endIdx) ->
                    adapter?.highlightText(
                        ListItemAdapter.ListItemHighlight(
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

        if (notallyModel.isNewNote || notallyModel.items.isEmpty()) {
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
            )
        val sortCallback =
            when (preferences.listItemSorting.value) {
                ListItemSort.AUTO_SORT_BY_CHECKED -> ListItemSortedByCheckedCallback(adapter)
                else -> ListItemNoSortCallback(adapter)
            }
        items = ListItemSortedList(sortCallback)
        val initializedItems = notallyModel.items.init(true)
        if (sortCallback is ListItemSortedByCheckedCallback) {
            sortCallback.setList(items)
            adapterChecked =
                ListItemAdapter(
                    colorInt,
                    notallyModel.textSize,
                    elevation,
                    NotallyXPreferences.getInstance(application),
                    listManager,
                    true,
                )
            itemsChecked = ListItemSortedList(ListItemNoSortCallback(adapterChecked))
            val (checkedItems, uncheckedItems) = initializedItems.splitByChecked()
            items.init(uncheckedItems)
            itemsChecked!!.init(checkedItems)
            adapter?.setList(items)
            adapterChecked?.setList(itemsChecked!!)
            binding.MainListView.adapter = adapter
            binding.CheckedListView.adapter = adapterChecked
            listManager.initList(items, itemsChecked)
        } else {
            items.init(initializedItems)
            adapter?.setList(items)
            binding.MainListView.adapter = adapter
            listManager.initList(items)
        }
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
    }

    companion object {
        private const val EXTRA_ITEM_POS = "notallyx.intent.extra.ITEM_POS"
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
