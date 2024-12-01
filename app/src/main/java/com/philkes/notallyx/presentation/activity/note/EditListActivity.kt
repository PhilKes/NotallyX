package com.philkes.notallyx.presentation.activity.note

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.changehistory.ChangeHistory

class EditListActivity : EditActivity(Type.LIST) {

    private lateinit var adapter: ListItemAdapter
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

    override fun setupToolbar() {
        super.setupToolbar()
        binding.Toolbar.menu.apply {
            add(
                R.string.delete_checked_items,
                R.drawable.delete_all,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
                groupId = 1,
            ) {
                listManager.deleteCheckedItems()
            }
            add(
                R.string.check_all_items,
                R.drawable.checkbox_fill,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
                groupId = 1,
            ) {
                listManager.changeCheckedForAll(true)
            }
            add(
                R.string.uncheck_all_items,
                R.drawable.checkbox,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
                groupId = 1,
            ) {
                listManager.changeCheckedForAll(false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setGroupDividerEnabled(true)
            }
        }
    }

    override fun initActionManager(undo: MenuItem, redo: MenuItem) {
        changeHistory = ChangeHistory {
            undo.isEnabled = changeHistory.canUndo()
            redo.isEnabled = changeHistory.canRedo()
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
            )
        adapter =
            ListItemAdapter(
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
        adapter.setList(items)
        binding.RecyclerView.adapter = adapter
        listManager.adapter = adapter
        listManager.initList(items)
    }
}
