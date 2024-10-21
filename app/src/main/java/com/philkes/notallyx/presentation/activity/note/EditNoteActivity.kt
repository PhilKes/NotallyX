package com.philkes.notallyx.presentation.activity.note

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.UpdateLinkDialogBinding
import com.philkes.notallyx.utils.LinkMovementMethod
import com.philkes.notallyx.utils.add
import com.philkes.notallyx.utils.changehistory.EditTextChange
import com.philkes.notallyx.utils.clone
import com.philkes.notallyx.utils.createTextWatcherWithHistory
import com.philkes.notallyx.utils.removeSelectionFromSpan
import com.philkes.notallyx.utils.setOnNextAction
import com.philkes.notallyx.utils.showKeyboard

class EditNoteActivity : EditActivity(Type.NOTE) {

    private lateinit var enterBodyTextWatcher: TextWatcher

    override fun configureUI() {
        binding.EnterTitle.setOnNextAction { binding.EnterBody.requestFocus() }

        setupEditor()

        if (model.isNewNote) {
            binding.EnterBody.requestFocus()
        }
    }

    override fun setupListeners() {
        super.setupListeners()
        enterBodyTextWatcher = run {
            binding.EnterBody.createTextWatcherWithHistory(
                changeHistory,
                { text, start, count ->
                    if (count > 1) {
                        val changedText = text.substring(start, start + count)
                        if (Patterns.WEB_URL.matcher(changedText).matches()) {
                            binding.EnterBody.text?.setSpan(
                                URLSpan(changedText),
                                start,
                                start + count,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                    }
                },
            ) { text: Editable ->
                model.body = text.clone()
            }
        }
        binding.EnterBody.addTextChangedListener(enterBodyTextWatcher)
    }

    override fun setStateFromModel() {
        super.setStateFromModel()
        updateEditText()
    }

    private fun updateEditText() {
        binding.EnterBody.apply {
            removeTextChangedListener(enterBodyTextWatcher)
            text = model.body
            addTextChangedListener(enterBodyTextWatcher)
        }
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
                        if (menu != null) {
                            menu.apply {
                                add(R.string.bold, 0) {
                                    applySpan(StyleSpan(Typeface.BOLD))
                                    mode?.finish()
                                }
                                add(R.string.link, 0) {
                                    val clipBoardText = getClipboardText()
                                    if (
                                        clipBoardText != null &&
                                            Patterns.WEB_URL.matcher(clipBoardText).matches()
                                    ) {
                                        applySpan(URLSpan(clipBoardText.toString()))
                                    } else {
                                        Toast.makeText(
                                                this@EditNoteActivity,
                                                R.string.invalid_link,
                                                Toast.LENGTH_LONG,
                                            )
                                            .show()
                                    }
                                    mode?.finish()
                                }
                                add(R.string.italic, 0) {
                                    applySpan(StyleSpan(Typeface.ITALIC))
                                    mode?.finish()
                                }
                                add(R.string.monospace, 0) {
                                    applySpan(TypefaceSpan("monospace"))
                                    mode?.finish()
                                }
                                add(R.string.strikethrough, 0) {
                                    applySpan(StrikethroughSpan())
                                    mode?.finish()
                                }
                                add(R.string.clear_formatting, 0) {
                                    clearFormatting()
                                    mode?.finish()
                                }
                            }
                        }
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                    return true
                }

                fun getClipboardText(): CharSequence? {
                    val clipboard = baseContext.getSystemService(ClipboardManager::class.java)!!
                    val clipData = clipboard.primaryClip!!
                    return if (clipData.itemCount > 0) clipData.getItemAt(0)!!.text else null
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    binding.EnterBody.isActionModeOn = false
                    model.body = binding.EnterBody.text!!.clone()
                }
            }

        binding.ContentLayout.setOnClickListener {
            binding.EnterBody.apply {
                requestFocus()
                setSelection(text!!.length)
                showKeyboard(this)
            }
        }
    }

    private fun setupMovementMethod() {
        val items =
            arrayOf(
                getString(R.string.copy),
                getString(R.string.edit),
                getString(R.string.open_link),
            )
        val movementMethod = LinkMovementMethod { span ->
            MaterialAlertDialogBuilder(this)
                .setTitle(span.url)
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> {
                            val clipboard: ClipboardManager =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("label", span.url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this, R.string.copied_link, Toast.LENGTH_LONG).show()
                        }
                        1 ->
                            showUrlInputDialog(span.url) { newUrl ->
                                binding.EnterBody.changeFormatting { _, _, text ->
                                    text.replaceUrlSpan(span, newUrl)
                                    model.body = text.clone()
                                    Toast.makeText(this, R.string.updated_link, Toast.LENGTH_LONG)
                                        .show()
                                }
                            }

                        2 -> {
                            if (span.url != null) {
                                val uri = Uri.parse(span.url)

                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                try {
                                    startActivity(intent)
                                } catch (exception: Exception) {
                                    Toast.makeText(this, R.string.cant_open_link, Toast.LENGTH_LONG)
                                        .show()
                                }
                            }
                        }
                    }
                }
                .show()
        }
        binding.EnterBody.movementMethod = movementMethod
    }

    private fun Editable.replaceUrlSpan(existingSpan: URLSpan, newUrl: String) {
        val start = this.getSpanStart(existingSpan)
        val end = this.getSpanEnd(existingSpan)
        if (start >= 0 && end >= 0) {
            this.removeSpan(existingSpan)
            val newSpan = URLSpan(newUrl)
            this.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun showUrlInputDialog(urlBefore: String, onSuccess: (newUrl: String) -> Unit) {
        val layout = UpdateLinkDialogBinding.inflate(layoutInflater)
        layout.InputText.setText(urlBefore)
        MaterialAlertDialogBuilder(this)
            .setView(layout.root)
            .setTitle(R.string.edit_link)
            .setPositiveButton(R.string.save) { _, _ ->
                val userInput = layout.InputText.text.toString()
                onSuccess.invoke(userInput)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
            .show()
        layout.InputText.requestFocus()
        showKeyboard(layout.InputText)
    }

    private fun clearFormatting() {
        binding.EnterBody.changeFormatting { start, end, text ->
            text.removeSelectionFromSpan(start, end)
        }
    }

    private fun applySpan(
        span: Any,
        start: Int = binding.EnterBody.selectionStart,
        end: Int = binding.EnterBody.selectionEnd,
    ) {
        binding.EnterBody.changeFormatting(start, end) { textStart, textEnd, text ->
            text.setSpan(span, textStart, textEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun EditText.changeFormatting(
        start: Int = selectionStart,
        end: Int = selectionEnd,
        change: (start: Int, end: Int, text: Editable) -> Unit,
    ) {
        ifBothNotNullAndInvalid(start, end) { textStart, textEnd ->
            val textBefore = text!!.clone()
            change(textStart, textEnd, text)
            val textAfter = text!!.clone()
            changeHistory.push(
                EditTextChange(this, textBefore, textAfter, enterBodyTextWatcher) { text ->
                    model.body = text.clone()
                }
            )
        }
    }

    private fun ifBothNotNullAndInvalid(
        start: Int?,
        end: Int?,
        function: (start: Int, end: Int) -> Unit,
    ) {
        if (start != null && start != -1 && end != null && end != -1) {
            function.invoke(start, end)
        }
    }

    companion object {

        fun getURLFrom(text: String): String {
            return when {
                text.matches(Patterns.PHONE.toRegex()) -> "tel:$text"
                text.matches(Patterns.EMAIL_ADDRESS.toRegex()) -> "mailto:$text"
                text.matches(Patterns.DOMAIN_NAME.toRegex()) -> "http://$text"
                else -> text
            }
        }
    }
}
