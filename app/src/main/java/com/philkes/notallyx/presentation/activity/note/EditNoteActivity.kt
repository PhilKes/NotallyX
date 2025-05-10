package com.philkes.notallyx.presentation.activity.note

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.data.model.getNoteIdFromUrl
import com.philkes.notallyx.data.model.getNoteTypeFromUrl
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.databinding.BottomTextFormattingMenuBinding
import com.philkes.notallyx.databinding.RecyclerToggleBinding
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_EXCLUDE_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TITLE
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXTRA_PICKED_NOTE_TYPE
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.createBoldSpan
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.hideKeyboard
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.note.TextFormattingAdapter
import com.philkes.notallyx.presentation.view.note.action.AddNoteActions
import com.philkes.notallyx.presentation.view.note.action.AddNoteBottomSheet
import com.philkes.notallyx.utils.LinkMovementMethod
import com.philkes.notallyx.utils.copyToClipBoard
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.wrapWithChooser

class EditNoteActivity : EditActivity(Type.NOTE), AddNoteActions {

    private lateinit var selectedSpan: URLSpan
    private lateinit var pickNoteNewActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickNoteUpdateActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var textFormatMenu: View

    private var textFormattingAdapter: TextFormattingAdapter? = null

    private var searchResultIndices: List<Pair<Int, Int>>? = null

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        if (notallyModel.isNewNote) {
            binding.EnterBody.requestFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupActivityResultLaunchers()
    }

    override fun toggleCanEdit(mode: NoteViewMode) {
        super.toggleCanEdit(mode)
        textFormatMenu.isVisible = mode == NoteViewMode.EDIT
        when {
            mode == NoteViewMode.EDIT -> showKeyboard(binding.EnterBody)
            binding.EnterBody.isFocused -> hideKeyboard(binding.EnterBody)
        }
        binding.EnterBody.setCanEdit(mode == NoteViewMode.EDIT)
        setupEditor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putInt(EXTRA_SELECTION_START, binding.EnterBody.selectionStart)
            putInt(EXTRA_SELECTION_END, binding.EnterBody.selectionEnd)
        }
    }

    private fun setupActivityResultLaunchers() {
        pickNoteNewActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        if (emptyTitle) {
                            binding.EnterBody.showAddLinkDialog(
                                this,
                                presetDisplayText = title,
                                presetUrl = url,
                                isNewUnnamedLink = true,
                            )
                        } else {
                            binding.EnterBody.addSpans(title, listOf(UnderlineSpan(), URLSpan(url)))
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
        pickNoteUpdateActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    try {
                        val (title, url, emptyTitle) = result.data.getPickedNoteData()
                        val newSpan = URLSpan(url)
                        binding.EnterBody.updateSpan(selectedSpan, newSpan, title)
                        if (emptyTitle) {
                            binding.EnterBody.showEditDialog(newSpan, isNewUnnamedLink = true)
                        }
                    } catch (_: IllegalArgumentException) {}
                }
            }
    }

    override fun highlightSearchResults(search: String): Int {
        binding.EnterBody.clearHighlights()
        if (search.isEmpty()) {
            return 0
        }
        searchResultIndices =
            notallyModel.body.toString().findAllOccurrences(search).onEach { (startIdx, endIdx) ->
                binding.EnterBody.highlight(startIdx, endIdx, false)
            }
        return searchResultIndices!!.size
    }

    override fun selectSearchResult(resultPos: Int) {
        if (resultPos < 0) {
            binding.EnterBody.unselectHighlight()
            return
        }
        searchResultIndices?.get(resultPos)?.let { (startIdx, endIdx) ->
            val selectedLineTop = binding.EnterBody.highlight(startIdx, endIdx, true)
            selectedLineTop?.let { binding.ScrollView.scrollTo(0, it) }
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.EnterBody.initHistory(changeHistory) { text ->
            val textChanged = !notallyModel.body.toString().contentEquals(text)
            notallyModel.body = text
            if (textChanged) {
                updateSearchResults(search.query)
            }
        }
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        updateEditText()
        savedInstanceState?.let {
            val selectionStart = it.getInt(EXTRA_SELECTION_START, -1)
            val selectionEnd = it.getInt(EXTRA_SELECTION_END, -1)
            if (selectionStart > -1) {
                binding.EnterBody.focusAndSelect(selectionStart, selectionEnd)
            }
        }
    }

    private fun updateEditText() {
        binding.EnterBody.text = notallyModel.body
    }

    private fun setupEditor() {
        setupMovementMethod()
        binding.EnterBody.customSelectionActionModeCallback =
            if (canEdit) {
                object : ActionMode.Callback {
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        binding.EnterBody.isActionModeOn = true
                        // Try block is there because this will crash on MiUI as Xiaomi has a broken
                        // ActionMode implementation
                        try {
                            menu?.apply {
                                add(
                                    R.string.link,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.showAddLinkDialog(
                                        this@EditNoteActivity,
                                        mode = mode,
                                    )
                                }
                                add(
                                    R.string.bold,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.applySpan(createBoldSpan())
                                    mode?.finish()
                                }
                                add(
                                    R.string.italic,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.applySpan(StyleSpan(Typeface.ITALIC))
                                    mode?.finish()
                                }
                                add(
                                    R.string.monospace,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.applySpan(TypefaceSpan("monospace"))
                                    mode?.finish()
                                }
                                add(
                                    R.string.strikethrough,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.applySpan(StrikethroughSpan())
                                    mode?.finish()
                                }
                                add(
                                    R.string.clear_formatting,
                                    0,
                                    showAsAction = MenuItem.SHOW_AS_ACTION_NEVER,
                                ) {
                                    binding.EnterBody.clearFormatting()
                                    mode?.finish()
                                }
                            }
                        } catch (exception: Exception) {
                            exception.printStackTrace()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        binding.EnterBody.isActionModeOn = false
                    }
                }
            } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.EnterBody.customInsertionActionModeCallback =
                if (canEdit) {
                    object : ActionMode.Callback {
                        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                            binding.EnterBody.isActionModeOn = true
                            // Try block is there because this will crash on MiUI as Xiaomi has a
                            // broken
                            // ActionMode implementation
                            try {
                                menu?.apply {
                                    add(
                                        R.string.link_note,
                                        0,
                                        order = Menu.CATEGORY_CONTAINER + 1,
                                    ) {
                                        linkNote(pickNoteNewActivityResultLauncher)
                                        mode?.finish()
                                    }
                                }
                            } catch (exception: Exception) {
                                exception.printStackTrace()
                            }
                            return true
                        }

                        override fun onDestroyActionMode(mode: ActionMode?) {
                            binding.EnterBody.isActionModeOn = false
                        }
                    }
                } else null
        }
        if (canEdit) {
            binding.EnterBody.setOnSelectionChange { selStart, selEnd ->
                if (selEnd - selStart > 0) {
                    if (!textFormatMenu.isEnabled) {
                        initBottomTextFormattingMenu()
                    }
                    textFormatMenu.isEnabled = true
                    textFormattingAdapter?.updateTextFormattingToggles(selStart, selEnd)
                } else {
                    if (textFormatMenu.isEnabled) {
                        initBottomMenu()
                    }
                    textFormatMenu.isEnabled = false
                }
            }
        } else {
            binding.EnterBody.setOnSelectionChange { _, _ -> }
        }
        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                if (canEdit) {
                    setSelection(length())
                    showKeyboard(this)
                }
            }
        }
    }

    override fun initBottomMenu() {
        super.initBottomMenu()
        binding.BottomAppBarCenter.visibility = VISIBLE
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            addIconButton(R.string.add_item, R.drawable.add, marginStart = 0) {
                AddNoteBottomSheet(this@EditNoteActivity, colorInt)
                    .show(supportFragmentManager, AddNoteBottomSheet.TAG)
            }
            updateLayoutParams<ConstraintLayout.LayoutParams> { endToStart = -1 }
            textFormatMenu =
                addIconButton(R.string.edit, R.drawable.text_format) {
                        initBottomTextFormattingMenu()
                    }
                    .apply { isEnabled = binding.EnterBody.isActionModeOn }
        }
        setBottomAppBarColor(colorInt)
    }

    private fun initBottomTextFormattingMenu() {
        binding.BottomAppBarCenter.visibility = GONE
        val extractColor = colorInt
        binding.BottomAppBarRight.apply {
            removeAllViews()
            addView(
                RecyclerToggleBinding.inflate(layoutInflater, this, false).root.apply {
                    setIconResource(R.drawable.close)
                    contentDescription = context.getString(R.string.cancel)
                    setOnClickListener { initBottomMenu() }

                    updateLayoutParams<LinearLayout.LayoutParams> {
                        marginEnd = 0
                        marginStart = 10.dp
                    }
                    setControlsContrastColorForAllViews(extractColor)
                    setBackgroundColor(0)
                }
            )
        }
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = R.id.BottomAppBarRight
            }
            requestLayout()
            val layout = BottomTextFormattingMenuBinding.inflate(layoutInflater, this, false)
            layout.MainListView.apply {
                textFormattingAdapter =
                    TextFormattingAdapter(this@EditNoteActivity, binding.EnterBody, colorInt)
                adapter = textFormattingAdapter
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
            addView(layout.root)
        }
    }

    override fun linkNote() {
        linkNote(pickNoteNewActivityResultLauncher)
    }

    fun linkNote(activityResultLauncher: ActivityResultLauncher<Intent>) {
        val intent =
            Intent(this, PickNoteActivity::class.java).apply {
                putExtra(EXTRA_EXCLUDE_NOTE_ID, notallyModel.id)
            }
        activityResultLauncher.launch(intent)
    }

    private fun setupMovementMethod() {
        val movementMethod = LinkMovementMethod { span ->
            val items =
                if (span.url.isNoteUrl()) {
                    if (canEdit) {
                        arrayOf(
                            getString(R.string.open_note),
                            getString(R.string.remove_link),
                            getString(R.string.change_note),
                            getString(R.string.edit),
                        )
                    } else arrayOf(getString(R.string.open_note))
                } else {
                    if (canEdit) {
                        arrayOf(
                            getString(R.string.open_link),
                            getString(R.string.copy),
                            getString(R.string.remove_link),
                            getString(R.string.edit),
                        )
                    } else arrayOf(getString(R.string.open_link), getString(R.string.copy))
                }
            MaterialAlertDialogBuilder(this)
                .setTitle(
                    if (span.url.isNoteUrl())
                        "${getString(R.string.note)}: ${
                            binding.EnterBody.getSpanText(span)
                        }"
                    else span.url
                )
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> openLink(span)
                        1 ->
                            if (span.url.isNoteUrl()) {
                                removeLink(span)
                            } else copyLink(span)
                        2 ->
                            if (span.url.isNoteUrl()) {
                                changeNoteLink(span)
                            } else removeLink(span)
                        3 -> editLink(span)
                    }
                }
                .show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }

    private fun openLink(span: URLSpan) {
        span.url?.let {
            if (it.isNoteUrl()) {
                span.navigateToNote()
            } else {
                openLink(span.url)
            }
        }
    }

    private fun editLink(span: URLSpan) {
        binding.EnterBody.showEditDialog(span)
    }

    private fun changeNoteLink(span: URLSpan) {
        selectedSpan = span
        linkNote(pickNoteUpdateActivityResultLauncher)
    }

    private fun copyLink(span: URLSpan) {
        copyToClipBoard(span.url)
        showToast(R.string.copied_link)
    }

    private fun removeLink(span: URLSpan) {
        binding.EnterBody.removeSpanWithHistory(
            span,
            span.url.isNoteUrl() || span.url == binding.EnterBody.getSpanText(span),
        )
    }

    private fun openLink(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).wrapWithChooser(this)
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            showToast(R.string.cant_open_link)
        }
    }

    private fun URLSpan.navigateToNote() {
        val noteId = url.getNoteIdFromUrl()
        val noteType = url.getNoteTypeFromUrl()
        when (noteType) {
            Type.NOTE -> goToActivity(EditNoteActivity::class.java, noteId)
            Type.LIST -> goToActivity(EditListActivity::class.java, noteId)
        }
    }

    private fun goToActivity(activity: Class<out Activity>, noteId: Long) {
        val intent = Intent(this, activity)
        intent.putExtra(EXTRA_SELECTED_BASE_NOTE, noteId)
        startActivity(intent)
    }

    private fun Intent?.getPickedNoteData(): Triple<String, String, Boolean> {
        val noteId = this?.getLongExtra(EXTRA_PICKED_NOTE_ID, -1L)!!
        if (noteId == -1L) {
            throw IllegalArgumentException("Invalid note picked!")
        }
        var emptyTitle = false
        val noteTitle =
            this.getStringExtra(EXTRA_PICKED_NOTE_TITLE)!!.ifEmpty {
                emptyTitle = true
                this@EditNoteActivity.getString(R.string.note)
            }
        val noteType = Type.valueOf(this.getStringExtra(EXTRA_PICKED_NOTE_TYPE)!!)
        val noteUrl = noteId.createNoteUrl(noteType)
        return Triple(noteTitle, noteUrl, emptyTitle)
    }

    companion object {
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
