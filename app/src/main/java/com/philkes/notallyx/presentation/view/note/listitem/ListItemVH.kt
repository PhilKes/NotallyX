package com.philkes.notallyx.presentation.view.note.listitem

import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.TextView.INVISIBLE
import android.widget.TextView.VISIBLE
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.view.misc.ListItemSorting
import com.philkes.notallyx.presentation.view.misc.SwipeLayout.SwipeActionsListener
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.utils.createListTextWatcherWithHistory
import com.philkes.notallyx.utils.setOnNextAction

class ListItemVH(
    val binding: RecyclerListItemBinding,
    val listManager: ListManager,
    touchHelper: ItemTouchHelper,
    textSize: String,
) : RecyclerView.ViewHolder(binding.root) {

    private var editTextWatcher: TextWatcher

    init {
        val body = TextSize.getEditBodySize(textSize)
        binding.EditText.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            setOnNextAction {
                val position = adapterPosition + 1
                listManager.add(position)
            }

            editTextWatcher =
                createListTextWatcherWithHistory(listManager, this@ListItemVH::getAdapterPosition)
            addTextChangedListener(editTextWatcher)

            setOnFocusChangeListener { _, hasFocus ->
                binding.Delete.visibility = if (hasFocus) VISIBLE else INVISIBLE
            }
        }

        binding.DragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchHelper.startDrag(this)
            }
            false
        }
    }

    fun bind(item: ListItem, firstItem: Boolean, autoSort: String) {
        updateEditText(item)

        updateCheckBox(item)

        updateDeleteButton(item)

        updateSwipe(item.isChild, !firstItem && !item.checked)
        if (item.checked && autoSort == ListItemSorting.autoSortByChecked) {
            binding.DragHandle.visibility = INVISIBLE
        } else {
            binding.DragHandle.visibility = VISIBLE
        }
    }

    fun focusEditText(
        selectionStart: Int = binding.EditText.text!!.length,
        inputMethodManager: InputMethodManager,
    ) {
        binding.EditText.apply {
            requestFocus()
            setSelection(selectionStart)
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateDeleteButton(item: ListItem) {
        binding.Delete.apply {
            visibility = if (item.checked) VISIBLE else INVISIBLE
            setOnClickListener { listManager.delete(adapterPosition) }
        }
    }

    private fun updateEditText(item: ListItem) {
        binding.EditText.apply {
            removeTextChangedListener(editTextWatcher)
            setText(item.body)
            isEnabled = !item.checked
            addTextChangedListener(editTextWatcher)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DEL) {
                    // TODO: when there are multiple checked items above it does not jump to the
                    // last
                    // unchecked item but always re-adds a new item
                    listManager.delete(adapterPosition, false)
                }
                true
            }
        }
    }

    private var checkBoxListener: OnCheckedChangeListener? = null

    private fun updateCheckBox(item: ListItem) {
        if (checkBoxListener == null) {
            checkBoxListener = OnCheckedChangeListener { buttonView, isChecked ->
                buttonView!!.setOnCheckedChangeListener(null)
                listManager.changeChecked(adapterPosition, isChecked)
                buttonView.setOnCheckedChangeListener(checkBoxListener)
            }
        }
        binding.CheckBox.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.checked
            setOnCheckedChangeListener(checkBoxListener)
        }
    }

    private fun updateSwipe(open: Boolean, canSwipe: Boolean) {
        binding.SwipeLayout.apply {
            setOnActionsListener(null)
            val swipeActionListener =
                object : SwipeActionsListener {
                    override fun onOpen(direction: Int, isContinuous: Boolean) {
                        listManager.changeIsChild(adapterPosition, true)
                    }

                    override fun onClose() {
                        listManager.changeIsChild(adapterPosition, false)
                    }
                }
            isEnabledSwipe = canSwipe
            post {
                if (open) {
                    openLeft(false)
                } else {
                    close(false)
                }
                setOnActionsListener(swipeActionListener)
            }
        }
    }
}
