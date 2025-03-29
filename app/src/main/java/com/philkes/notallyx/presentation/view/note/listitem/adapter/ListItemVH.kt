package com.philkes.notallyx.presentation.view.note.listitem.adapter

import android.graphics.Paint
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View.GONE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.EditText
import android.widget.TextView.INVISIBLE
import android.widget.TextView.VISIBLE
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import cn.leaqi.drawer.SwipeDrawer.DIRECTION_LEFT
import cn.leaqi.drawer.SwipeDrawer.STATE_CLOSE
import cn.leaqi.drawer.SwipeDrawer.STATE_OPEN
import com.philkes.notallyx.data.imports.txt.extractListItems
import com.philkes.notallyx.data.imports.txt.findListSyntaxRegex
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.clone
import com.philkes.notallyx.presentation.createListTextWatcherWithHistory
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.misc.EditTextAutoClearFocus
import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.firstBodyOrEmptyString
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NoteViewMode
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize
import com.philkes.notallyx.utils.changehistory.EditTextState
import com.philkes.notallyx.utils.copyToClipBoard

class ListItemVH(
    val binding: RecyclerListItemBinding,
    val listManager: ListManager,
    touchHelper: ItemTouchHelper,
    textSize: TextSize,
    private val inCheckedList: Boolean,
) : RecyclerView.ViewHolder(binding.root) {

    private var dragHandleInitialY: Float = 0f

    init {
        val body = textSize.editBodySize
        binding.EditText.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            textWatcher =
                createListTextWatcherWithHistory(
                    listManager,
                    this@ListItemVH::getAdapterPosition,
                ) { text, start, count ->
                    if (count > 1) {
                        checkListPasted(text, start, count, this)
                    } else {
                        false
                    }
                }
        }

        binding.DragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragHandleInitialY = event.y
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL -> {
                    val dY = Math.abs(dragHandleInitialY!! - event.y)
                    if (dY > binding.DragHandle.measuredHeight * 0.15f) {
                        touchHelper.startDrag(this)
                    }
                }
            }
            false
        }

        binding.SwipeLayout.setOnDrawerChange { view, state, progress ->
            when (state) {
                STATE_OPEN -> listManager.changeIsChild(absoluteAdapterPosition, true)
                STATE_CLOSE -> listManager.changeIsChild(absoluteAdapterPosition, false)
            }
        }
    }

    fun bind(
        @ColorInt backgroundColor: Int,
        item: ListItem,
        position: Int,
        highlights: List<ListItemHighlight>?,
        autoSort: ListItemSort,
        viewMode: NoteViewMode,
    ) {
        updateEditText(item, position, viewMode)

        updateCheckBox(item, position)

        updateDeleteButton(item, position, viewMode)

        updateSwipe(item.isChild, viewMode == NoteViewMode.EDIT && position != 0 && !item.checked)
        binding.DragHandle.apply {
            visibility =
                when {
                    viewMode != NoteViewMode.EDIT -> GONE
                    item.checked && autoSort == ListItemSort.AUTO_SORT_BY_CHECKED -> INVISIBLE
                    else -> VISIBLE
                }
            contentDescription = "Drag$position"
        }

        highlights?.let {
            it.forEach { highlight ->
                binding.EditText.highlight(highlight.startIdx, highlight.endIdx, highlight.selected)
            }
        } ?: binding.EditText.clearHighlights()

        binding.root.setControlsContrastColorForAllViews(backgroundColor)
    }

    fun focusEditText(
        selectionStart: Int = binding.EditText.text!!.length,
        selectionEnd: Int = selectionStart,
        inputMethodManager: InputMethodManager?,
    ) {
        binding.EditText.focusAndSelect(selectionStart, selectionEnd, inputMethodManager)
    }

    private fun updateDeleteButton(item: ListItem, position: Int, viewMode: NoteViewMode) {
        binding.Delete.apply {
            visibility =
                when {
                    viewMode != NoteViewMode.EDIT -> GONE
                    item.checked -> VISIBLE
                    else -> INVISIBLE
                }
            setOnClickListener {
                listManager.delete(absoluteAdapterPosition, inCheckedList = inCheckedList)
            }
            contentDescription = "Delete$position"
        }
    }

    private fun updateEditText(item: ListItem, position: Int, viewMode: NoteViewMode) {
        binding.EditText.apply {
            setText(item.body)
            paintFlags =
                if (item.checked) {
                    paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
            alpha = if (item.checked) 0.5f else 1.0f
            contentDescription = "EditText$position"
            if (viewMode == NoteViewMode.EDIT) {
                setOnFocusChangeListener { _, hasFocus ->
                    binding.Delete.visibility = if (hasFocus) VISIBLE else INVISIBLE
                }
                binding.Content.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            } else {
                onFocusChangeListener = null
                binding.Content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            setCanEdit(viewMode == NoteViewMode.EDIT)
            when (viewMode) {
                NoteViewMode.EDIT -> {
                    setOnClickListener(null)
                    setOnLongClickListener(null)
                }
                NoteViewMode.READ_ONLY -> {
                    setOnClickListener {
                        if (absoluteAdapterPosition != NO_POSITION) {
                            listManager.changeChecked(
                                absoluteAdapterPosition,
                                !item.checked,
                                inCheckedList,
                            )
                        }
                    }
                    setOnLongClickListener {
                        context?.copyToClipBoard(item.body)
                        true
                    }
                }
            }
            setOnNextAction { listManager.add(bindingAdapterPosition + 1) }
            setOnKeyListener { _, keyCode, event ->
                if (
                    event.action == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DEL &&
                        item.body.isEmpty()
                ) {
                    // TODO: when there are multiple checked items above it does not jump to the
                    // last
                    // unchecked item but always re-adds a new item
                    listManager.delete(
                        absoluteAdapterPosition,
                        inCheckedList = inCheckedList,
                        force = false,
                    )
                } else {
                    false
                }
            }
        }
    }

    private var checkBoxListener: OnCheckedChangeListener? = null

    private fun updateCheckBox(item: ListItem, position: Int) {
        if (checkBoxListener == null) {
            checkBoxListener = OnCheckedChangeListener { _, isChecked ->
                binding.CheckBox.setOnCheckedChangeListener(null)
                if (absoluteAdapterPosition != NO_POSITION) {
                    listManager.changeChecked(absoluteAdapterPosition, isChecked, inCheckedList)
                }
                binding.CheckBox.setOnCheckedChangeListener(checkBoxListener)
            }
        }
        binding.CheckBox.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.checked
            setOnCheckedChangeListener(checkBoxListener)
            contentDescription = "CheckBox$position"
        }
    }

    private fun updateSwipe(open: Boolean, canSwipe: Boolean) {
        binding.SwipeLayout.apply {
            intercept = canSwipe
            post {
                if (open) {
                    openDrawer(DIRECTION_LEFT, false, false)
                } else {
                    closeDrawer(DIRECTION_LEFT, false, false)
                }
            }
        }
    }

    private fun checkListPasted(
        text: CharSequence,
        start: Int,
        count: Int,
        editText: EditTextAutoClearFocus,
    ): Boolean {
        val changedText = text.substring(start, start + count)
        val containsLines = changedText.isNotBlank() && changedText.contains("\n")
        if (containsLines) {
            changedText
                .findListSyntaxRegex(checkContains = true, plainNewLineAllowed = true)
                ?.let { listSyntaxRegex ->
                    val items = changedText.extractListItems(listSyntaxRegex)
                    if (items.isNotEmpty()) {
                        listManager.startBatchChange(start)
                        val position = absoluteAdapterPosition
                        val itemHadTextBefore = text.trim().length > count
                        val firstPastedItemBody = items.firstBodyOrEmptyString()
                        val updatedText =
                            if (itemHadTextBefore) {
                                text.substring(0, start) + firstPastedItemBody
                            } else firstPastedItemBody
                        editText.changeText(position, updatedText)
                        items.drop(1).forEachIndexed { index, it ->
                            listManager.add(position + 1 + index, it, pushChange = false)
                        }
                        listManager.finishBatchChange(position + items.size - 1)
                    }
                }
        }
        return containsLines
    }

    private fun EditText.changeText(position: Int, after: CharSequence) {
        setText(after)
        val stateAfter = EditTextState(editableText.clone(), selectionStart)
        listManager.changeText(position, stateAfter, pushChange = false)
    }

    fun getSelection() = with(binding.EditText) { Pair(selectionStart, selectionEnd) }
}
