package com.philkes.notallyx.presentation.activity.note

import android.os.Build
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import com.philkes.notallyx.Preferences
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.misc.ListItemSorting
import com.philkes.notallyx.presentation.view.note.listitem.ListItemAdapter
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemNoSortCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedByCheckedCallback
import com.philkes.notallyx.presentation.view.note.listitem.sorting.ListItemSortedList
import com.philkes.notallyx.presentation.view.note.listitem.sorting.toMutableList
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.changehistory.ChangeHistory

class EditListActivity : EditActivity(Type.LIST) {

    private lateinit var adapter: ListItemAdapter
    private lateinit var items: ListItemSortedList

    private lateinit var listManager: ListManager

    override suspend fun saveNote() {
        super.saveNote()
        model.saveNote(items.toMutableList())
        WidgetProvider.sendBroadcast(application, longArrayOf(model.id))
    }

    override fun setupToolbar() {
        super.setupToolbar()
        binding.Toolbar.menu.apply {
            add(
                1,
                R.string.delete_checked_items,
                R.drawable.delete_all,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
            ) {
                listManager.deleteCheckedItems()
            }
            add(
                1,
                R.string.check_all_items,
                R.drawable.checkbox_fill,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
            ) {
                listManager.changeCheckedForAll(true)
            }
            add(
                1,
                R.string.uncheck_all_items,
                R.drawable.checkbox,
                MenuItem.SHOW_AS_ACTION_IF_ROOM,
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
                getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager,
            )
        adapter =
            ListItemAdapter(
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
