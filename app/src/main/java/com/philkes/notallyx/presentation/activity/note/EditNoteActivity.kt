package com.philkes.notallyx.presentation.activity.note

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.createNoteUrl
import com.philkes.notallyx.data.model.getNoteIdFromUrl
import com.philkes.notallyx.data.model.getNoteTypeFromUrl
import com.philkes.notallyx.data.model.isNoteUrl
import com.philkes.notallyx.data.model.isWebUrl
import com.philkes.notallyx.databinding.TextInputDialog2Binding
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.EXCLUDE_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.PICKED_NOTE_ID
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.PICKED_NOTE_TITLE
import com.philkes.notallyx.presentation.activity.note.PickNoteActivity.Companion.PICKED_NOTE_TYPE
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.copyToClipBoard
import com.philkes.notallyx.presentation.getLatestText
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.utils.LinkMovementMethod

private const val UNNAMED_NOTE_PLACEHOLDER = "Unnamed Note"

class EditNoteActivity : EditActivity(Type.NOTE) {

    private lateinit var selectedSpan: URLSpan
    private lateinit var pickNoteNewActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickNoteUpdateActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        setupEditor()

        if (model.isNewNote) {
            binding.EnterBody.requestFocus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupActivityResultLaunchers()
    }

    private fun setupActivityResultLaunchers() {
        pickNoteNewActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    val noteId = data?.getLongExtra(PICKED_NOTE_ID, -1L)!!
                    if (noteId == -1L) {
                        return@registerForActivityResult
                    }
                    val noteTitle = data.getStringExtra(PICKED_NOTE_TITLE)!!
                    val noteType = Type.valueOf(data.getStringExtra(PICKED_NOTE_TYPE)!!)
                    binding.EnterBody.addSpan(noteTitle, URLSpan(noteId.createNoteUrl(noteType)))
                }
            }
        pickNoteUpdateActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data
                    val noteId = data?.getLongExtra(PICKED_NOTE_ID, -1L)!!
                    if (noteId == -1L) {
                        return@registerForActivityResult
                    }
                    // TODO: If the linked note title changes the link display text does not change
                    val noteTitle =
                        data.getStringExtra(PICKED_NOTE_TITLE)!!.ifEmpty {
                            UNNAMED_NOTE_PLACEHOLDER
                        }
                    val noteType = Type.valueOf(data.getStringExtra(PICKED_NOTE_TYPE)!!)
                    val noteUrl = noteId.createNoteUrl(noteType)
                    binding.EnterBody.updateSpan(selectedSpan, URLSpan(noteUrl), noteTitle)
                }
            }
    }

    override fun setupListeners() {
        super.setupListeners()
        binding.EnterBody.initHistory(changeHistory) { text -> model.body = text }
    }

    override fun setStateFromModel() {
        super.setStateFromModel()
        updateEditText()
    }

    private fun updateEditText() {
        binding.EnterBody.text = model.body
    }

    private fun setupEditor() {
        setupMovementMethod()

        binding.EnterBody.customSelectionActionModeCallback =
            object : ActionMode.Callback {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    binding.EnterBody.isActionModeOn = true
                    // Try block is there because this will crash on MiUI as Xiaomi has a broken
                    // ActionMode implementation
                    try {
                        menu?.apply {
                            add(R.string.bold, 0) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.BOLD))
                                mode?.finish()
                            }
                            add(R.string.link, 0) { showAddLinkDialog(mode) }
                            add(R.string.italic, 0) {
                                binding.EnterBody.applySpan(StyleSpan(Typeface.ITALIC))
                                mode?.finish()
                            }
                            add(R.string.monospace, 0) {
                                binding.EnterBody.applySpan(TypefaceSpan("monospace"))
                                mode?.finish()
                            }
                            add(R.string.strikethrough, 0) {
                                binding.EnterBody.applySpan(StrikethroughSpan())
                                mode?.finish()
                            }
                            add(R.string.clear_formatting, 0) {
                                binding.EnterBody.clearFormatting()
                                mode?.finish()
                            }
                        }
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                    return true
                }

                private fun showAddLinkDialog(mode: ActionMode?) {
                    val urlFromClipboard =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            baseContext
                                .getSystemService(ClipboardManager::class.java)!!
                                .getLatestText()
                                .let { if (it.isWebUrl()) it.toString() else "" }
                        } else ""
                    val displayTextBefore = binding.EnterBody.getSelectionText()!!
                    this@EditNoteActivity.showEditLinkDialog(urlFromClipboard, displayTextBefore) {
                        urlAfter,
                        displayTextAfter ->
                        if (displayTextAfter == displayTextBefore) {
                            binding.EnterBody.applySpan(URLSpan(urlAfter))
                        } else {
                            binding.EnterBody.changeTextWithHistory { text ->
                                val start = binding.EnterBody.selectionStart
                                text.replace(
                                    start,
                                    binding.EnterBody.selectionEnd,
                                    displayTextAfter,
                                )
                                text.setSpan(
                                    URLSpan(urlAfter),
                                    start,
                                    start + displayTextAfter.length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                )
                            }
                        }
                        mode?.finish()
                    }
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    binding.EnterBody.isActionModeOn = false
                }
            }

        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.EnterBody.customInsertionActionModeCallback =
                object : ActionMode.Callback {
                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        binding.EnterBody.isActionModeOn = true
                        // Try block is there because this will crash on MiUI as Xiaomi has a broken
                        // ActionMode implementation
                        try {
                            menu?.apply {
                                add(R.string.link_note, 0) {
                                    startPickNote(pickNoteNewActivityResultLauncher)
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
        }
        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(length())
                showKeyboard(this)
            }
        }
    }

    private fun EditNoteActivity.startPickNote(
        activityResultLauncher: ActivityResultLauncher<Intent>
    ) {
        val intent =
            Intent(this, PickNoteActivity::class.java).apply { putExtra(EXCLUDE_NOTE_ID, model.id) }
        activityResultLauncher.launch(intent)
    }

    private fun setupMovementMethod() {
        val movementMethod = LinkMovementMethod { span ->
            val items =
                if (span.url.isNoteUrl()) {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.change_note),
                        getString(R.string.open_note),
                    )
                } else {
                    arrayOf(
                        getString(R.string.remove_link),
                        getString(R.string.copy),
                        getString(R.string.edit),
                        getString(R.string.open_link),
                    )
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
                        0 -> {
                            binding.EnterBody.removeSpan(span, true)
                        }
                        1 ->
                            if (span.url.isNoteUrl()) {
                                selectedSpan = span
                                startPickNote(pickNoteUpdateActivityResultLauncher)
                            } else {
                                copyToClipBoard(span.url)
                                Toast.makeText(this, R.string.copied_link, Toast.LENGTH_LONG).show()
                            }

                        2 -> {
                            span.url?.let {
                                if (it.isNoteUrl()) {
                                    span.navigateToNote()
                                } else {
                                    span.showEditDialog()
                                }
                            }
                        }

                        3 -> {
                            openLink(span.url)
                        }
                    }
                }
                .show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }

    private fun openLink(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            Toast.makeText(this, R.string.cant_open_link, Toast.LENGTH_LONG).show()
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

    private fun URLSpan.showEditDialog() {
        val displayTextBefore = binding.EnterBody.getSpanText(this)
        showEditLinkDialog(url, displayTextBefore) { urlAfter, displayTextAfter ->
            if (urlAfter != null) {
                binding.EnterBody.updateSpan(
                    this,
                    URLSpan(urlAfter),
                    if (displayTextAfter == displayTextBefore) null else displayTextAfter,
                )
            } else {
                binding.EnterBody.removeSpan(this)
            }
        }
    }

    private fun goToActivity(activity: Class<out Activity>, noteId: Long) {
        val intent = Intent(this, activity)
        intent.putExtra(Constants.SelectedBaseNote, noteId)
        startActivity(intent)
    }

    private fun showEditLinkDialog(
        urlBefore: String,
        displayTextBefore: String,
        onSuccess: (urlAfter: String?, displayTextAfter: String) -> Unit,
    ) {
        val layout = TextInputDialog2Binding.inflate(layoutInflater)
        layout.InputText1.apply { setText(displayTextBefore) }
        layout.InputTextLayout1.setHint(R.string.display_text)
        layout.InputText2.apply { setText(urlBefore) }

        layout.InputTextLayout2.setHint(R.string.link)
        MaterialAlertDialogBuilder(this)
            .setView(layout.root)
            .setTitle(R.string.edit_link)
            .setPositiveButton(R.string.save) { _, _ ->
                val displayTextAfter = layout.InputText1.text.toString()
                val urlAfter = layout.InputText2.text.toString()
                onSuccess.invoke(urlAfter, displayTextAfter)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.clear) { dialog, _ ->
                dialog.cancel()
                onSuccess.invoke(null, displayTextBefore)
            }
            .showAndFocus(layout.InputText2)
    }
}
