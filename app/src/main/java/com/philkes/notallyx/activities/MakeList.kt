package com.philkes.notallyx.activities

import android.os.Build
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import com.philkes.notallyx.R
import com.philkes.notallyx.changehistory.ChangeHistory
import com.philkes.notallyx.miscellaneous.add
import com.philkes.notallyx.miscellaneous.setOnNextAction
import com.philkes.notallyx.model.Type
import com.philkes.notallyx.preferences.ListItemSorting
import com.philkes.notallyx.preferences.Preferences
import com.philkes.notallyx.recyclerview.ListManager
import com.philkes.notallyx.recyclerview.adapter.MakeListAdapter
import com.philkes.notallyx.sorting.ListItemNoSortCallback
import com.philkes.notallyx.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.sorting.ListItemSortedList
import com.philkes.notallyx.sorting.toMutableList
import com.philkes.notallyx.widget.WidgetProvider

class MakeList : NotallyXActivity(Type.LIST) {

    private lateinit var adapter: MakeListAdapter
    private lateinit var items: ListItemSortedList

    private lateinit var listManager: ListManager

    override suspend fun saveNote() {
        model.saveNote(items.toMutableList())
        WidgetProvider.sendBroadcast(application, longArrayOf(model.id))
    }

    override fun setupToolbar() {
        super.setupToolbar()
        binding.Toolbar.menu.add(
            1,
            R.string.delete_checked_items,
            R.drawable.delete_all,
            MenuItem.SHOW_AS_ACTION_IF_ROOM,
        ) {
            listManager.deleteCheckedItems()
        }
        binding.Toolbar.menu.add(
            1,
            R.string.check_all_items,
            R.drawable.checkbox_fill,
            MenuItem.SHOW_AS_ACTION_IF_ROOM,
        ) {
            listManager.changeCheckedForAll(true)
        }
        binding.Toolbar.menu.add(
            1,
            R.string.uncheck_all_items,
            R.drawable.checkbox,
            MenuItem.SHOW_AS_ACTION_IF_ROOM,
        ) {
            listManager.changeCheckedForAll(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.Toolbar.menu.setGroupDividerEnabled(true)
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
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager,
            )
        adapter =
            MakeListAdapter(
                model.textSize,
                elevation,
                Preferences.getInstance(application),
                listManager,
            )
        val sortCallback =
            when (preferences.listItemSorting.value) {
                ListItemSorting.autoSortByChecked -> ListItemSortedByCheckedCallback(adapter)
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
