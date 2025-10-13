package com.philkes.notallyx.presentation.activity.note

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.findAllOccurrences
import com.philkes.notallyx.utils.getFileName
import com.philkes.notallyx.utils.mergeSkipFirst
import com.philkes.notallyx.utils.observeSkipFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for editing plain text files without formatting options. This editor provides a simple
 * text editing experience with change history but without any text formatting or attachment
 * capabilities.
 */
class EditTextPlainActivity : EditActivity(Type.NOTE) {

    private var searchResultIndices: List<Pair<Int, Int>>? = null
    private var originalFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Store the original URI if this is a txt file being opened
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            originalFileUri = intent.data
        }
        super.onCreate(savedInstanceState)
    }

    override suspend fun checkSave() {}

    override fun configureUI() {
        // Set the file name as the title and make it non-editable
        originalFileUri?.let { uri ->
            val fileName = getFileName(uri)
            if (!fileName.isNullOrEmpty()) {
                notallyModel.title = fileName
                binding.EnterTitle.setText(fileName)
                binding.EnterTitle.isEnabled = false
            }
        }

        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        if (notallyModel.isNewNote) {
            binding.EnterBody.requestFocus()
        }
        // Disable text formatting by providing a null callback
        binding.EnterBody.customSelectionActionModeCallback = null
        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
    }

    override fun toggleCanEdit(mode: NoteViewMode) {}

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            putInt(EXTRA_SELECTION_START, binding.EnterBody.selectionStart)
            putInt(EXTRA_SELECTION_END, binding.EnterBody.selectionEnd)
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

    override fun setupToolbars() {
        binding.Toolbar.setNavigationIcon(R.drawable.close)
        binding.Toolbar.setNavigationOnClickListener { finish() }
        binding.Toolbar.menu.apply {
            clear()
            add(R.string.search, R.drawable.search, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                startSearch()
            }
            // Pin action removed
        }

        search.results.mergeSkipFirst(search.resultPos).observe(this) { (amount, pos) ->
            val hasResults = amount > 0
            binding.SearchResults.text = if (hasResults) "${pos + 1}/$amount" else "0"
            search.nextMenuItem?.isEnabled = hasResults
            search.prevMenuItem?.isEnabled = hasResults
        }

        search.resultPos.observeSkipFirst(this) { pos -> selectSearchResult(pos) }

        binding.EnterSearchKeyword.apply {
            doAfterTextChanged { text ->
                this@EditTextPlainActivity.search.query = text.toString()
                updateSearchResults(this@EditTextPlainActivity.search.query)
            }
        }
        initBottomMenu()
    }

    override fun setStateFromModel(savedInstanceState: Bundle?) {
        super.setStateFromModel(savedInstanceState)
        // Remove the modified time display
        binding.Date.visibility = GONE

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

    override fun initBottomMenu() {
        super.initBottomMenu()
        binding.BottomAppBarCenter.visibility = VISIBLE
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            addIconButton(
                R.string.convert_to_text_note,
                R.drawable.convert_to_text,
                marginStart = 10,
            ) {
                convertToTextNote()
            }
        }
        binding.BottomAppBarRight.apply {
            removeAllViews()
            if (originalFileUri != null) {
                addIconButton(R.string.save_to_device, R.drawable.save, marginStart = 10) {
                    saveToOriginalFile()
                }
            }
        }
        setBottomAppBarColor(colorInt)
    }

    override fun initChangeHistory() {
        changeHistory =
            ChangeHistory().apply {
                canUndo.observe(this@EditTextPlainActivity) { canUndo -> undo?.isEnabled = canUndo }
                canRedo.observe(this@EditTextPlainActivity) { canRedo -> redo?.isEnabled = canRedo }
            }
    }

    /** Saves the current content back to the original txt file */
    private fun saveToOriginalFile() {
        originalFileUri?.let { uri ->
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(notallyModel.body.toString().toByteArray())
                        }
                    }
                    showToast(R.string.saved_to_device)
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(this@EditTextPlainActivity)
                        .setTitle(R.string.something_went_wrong)
                        .setMessage(e.message)
                        .setCancelButton()
                        .show()
                }
            }
        }
    }

    // We can't override createNoteTypeActions, so we'll add a button to convert to regular note
    // in the bottom menu instead
    private fun convertToTextNote() {
        lifecycleScope.launch {
            // Save the current note
            saveNote(checkAutoSave = false)

            // Create a new intent to open the note in EditNoteActivity
            val intent = Intent(this@EditTextPlainActivity, EditNoteActivity::class.java)
            intent.putExtra(EXTRA_SELECTED_BASE_NOTE, notallyModel.id)
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val EXTRA_SELECTION_START = "notallyx.intent.extra.EXTRA_SELECTION_START"
        private const val EXTRA_SELECTION_END = "notallyx.intent.extra.EXTRA_SELECTION_END"
    }
}
