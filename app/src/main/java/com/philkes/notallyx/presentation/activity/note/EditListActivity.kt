package com.philkes.notallyx.presentation.activity.note

import android.os.Bundle
import android.view.View.GONE
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.note.action.MoreListActions
import com.philkes.notallyx.presentation.view.note.action.MoreListBottomSheet
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.mapIndexed
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.findAllOccurrences

class EditListActivity : EditActivity(Type.LIST), MoreListActions {

    private var adapter: ListItemAdapter? = null
    private lateinit var items: ListItemSortedList

    private lateinit var listManager: ListManager

    override fun finish() {
        model.setItems(items.toMutableList())
        super.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        model.setItems(items.toMutableList())
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
                val additionalActions = listOf(createPinAction()) + createFolderActions()
                MoreListBottomSheet(this@EditListActivity, additionalActions)
                    .show(supportFragmentManager, MoreListBottomSheet.TAG)
            }
        }
    }

    override fun highlightSearchResults(search: String): Int {
        var resultPos = 0
        adapter?.clearHighlights()
        return items
            .mapIndexed { idx, item ->
                val occurrences = item.body.findAllOccurrences(search)
                occurrences.onEach { (startIdx, endIdx) ->
                    adapter?.highlightText(
                        ListItemAdapter.ListItemHighlight(idx, resultPos++, startIdx, endIdx, false)
                    )
                }
                occurrences.size
            }
            .sum()
    }

    override fun selectSearchResult(resultPos: Int) {
        val selectedItemPos = adapter!!.selectHighlight(resultPos)
        if (selectedItemPos != -1) {
            binding.RecyclerView.post {
                binding.RecyclerView.findViewHolderForAdapterPosition(selectedItemPos)
                    ?.itemView
                    ?.let { binding.ScrollView.scrollTo(0, binding.RecyclerView.top + it.top) }
            }
        }
    }

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { listManager.moveFocusToNext(-1) }

        if (model.isNewNote || model.items.isEmpty()) {
            listManager.add(pushChange = false)
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.AddItem.setOnClickListener { listManager.add() }
    }

    override fun setStateFromModel() {
        super.setStateFromModel()
        val elevation = resources.displayMetrics.density * 2
        listManager =
            ListManager(
                binding.RecyclerView,
                changeHistory,
                preferences,
                ContextCompat.getSystemService(baseContext, InputMethodManager::class.java),
            ) {
                if (binding.EnterSearchKeyword.visibility != GONE) {
                    endSearch()
                }
            }
        adapter =
            ListItemAdapter(
                Operations.extractColor(model.color, this),
                model.textSize,
                elevation,
                NotallyXPreferences.getInstance(application),
                listManager,
            )
        val sortCallback =
            when (preferences.listItemSorting.value) {
                ListItemSort.AUTO_SORT_BY_CHECKED -> ListItemSortedByCheckedCallback(adapter)
                else -> ListItemNoSortCallback(adapter)
            }
        items = ListItemSortedList(sortCallback)
        if (sortCallback is ListItemSortedByCheckedCallback) {
            sortCallback.setList(items)
        }
        items.init(model.items)
        adapter?.setList(items)
        binding.RecyclerView.adapter = adapter
        listManager.adapter = adapter!!
        listManager.initList(items)
    }

    override fun setColor() {
        super.setColor()
        adapter?.setBackgroundColor(Operations.extractColor(model.color, this))
    }
}
