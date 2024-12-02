package com.philkes.notallyx.presentation.activity.note

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.VISIBLE
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.ActivityEditBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.getUriForFile
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.showColorSelectDialog
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.note.ErrorAdapter
import com.philkes.notallyx.presentation.view.note.audio.AudioAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewFileAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewImageAdapter
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.mergeSkipFirst
import com.philkes.notallyx.utils.observeSkipFirst
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class EditActivity(private val type: Type) : LockedActivity<ActivityEditBinding>() {
    private lateinit var recordAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var addImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var selectLabelsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var playAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var attachFilesActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var searchPosPrev: MenuItem
    private lateinit var searchPosNext: MenuItem
    private val searchResultPos = NotNullLiveData(-1)
    private val searchResultsAmount = NotNullLiveData(-1)

    internal val model: NotallyModel by viewModels()
    internal lateinit var changeHistory: ChangeHistory

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (model.isEmpty()) {
                model.deleteBaseNote()
            } else if (model.isModified()) {
                saveNote()
            }
            super.finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", model.id)
        if (model.isModified()) {
            lifecycleScope.launch { saveNote() }
        }
    }

    open suspend fun saveNote() {
        model.modifiedTimestamp = System.currentTimeMillis()
        model.saveNote()
        WidgetProvider.sendBroadcast(application, longArrayOf(model.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.type = type
        initialiseBinding()
        setContentView(binding.root)

        lifecycleScope.launch {
            val persistedId = savedInstanceState?.getLong("id")
            val selectedId = intent.getLongExtra(Constants.SelectedBaseNote, 0L)
            val id = persistedId ?: selectedId
            model.setState(id)

            if (model.isNewNote && intent.action == Intent.ACTION_SEND) {
                handleSharedNote()
            } else if (model.isNewNote) {
                intent.getStringExtra(Constants.SelectedLabel)?.let { model.setLabels(listOf(it)) }
            }

            setupToolbar()
            setupListeners()
            setStateFromModel()

            configureUI()
            binding.ScrollView.visibility = View.VISIBLE
        }

        setupActivityResultLaunchers()
    }

    private fun setupActivityResultLaunchers() {
        recordAudioActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    model.addAudio()
                }
            }
        addImagesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    val clipData = result.data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        model.addImages(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        model.addImages(uris)
                    }
                }
            }
        viewImagesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val list =
                        result.data?.let {
                            IntentCompat.getParcelableArrayListExtra(
                                it,
                                ViewImageActivity.DELETED_IMAGES,
                                FileAttachment::class.java,
                            )
                        }
                    if (!list.isNullOrEmpty()) {
                        model.deleteImages(list)
                    }
                }
            }
        selectLabelsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val list =
                        result.data?.getStringArrayListExtra(SelectLabelsActivity.SELECTED_LABELS)
                    if (list != null && list != model.labels) {
                        model.setLabels(list)
                        Operations.bindLabels(binding.LabelGroup, model.labels, model.textSize)
                    }
                }
            }
        playAudioActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val audio =
                        result.data?.let {
                            IntentCompat.getParcelableExtra(
                                it,
                                PlayAudioActivity.AUDIO,
                                Audio::class.java,
                            )
                        }
                    if (audio != null) {
                        model.deleteAudio(audio)
                    }
                }
            }
        attachFilesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    val clipData = result.data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        model.addFiles(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        model.addFiles(uris)
                    }
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_AUDIO_PERMISSION -> {
                if (
                    grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    recordAudio()
                } else handleRejection()
            }
        }
    }

    protected open fun initActionManager(undo: MenuItem, redo: MenuItem) {
        changeHistory = ChangeHistory {
            undo.isEnabled = changeHistory.canUndo()
            redo.isEnabled = changeHistory.canRedo()
        }
        undo.isEnabled = changeHistory.canUndo()
        redo.isEnabled = changeHistory.canRedo()
    }

    protected open fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }

        binding.Toolbar.menu.apply {
            clear()
            add(R.string.search, R.drawable.search, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                startSearch()
            }
            val undo =
                add(R.string.undo, R.drawable.undo, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                    changeHistory.undo()
                }
            val redo =
                add(R.string.redo, R.drawable.redo, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                    changeHistory.redo()
                }

            initActionManager(undo, redo)

            val pin = add(R.string.pin, R.drawable.pin) { item -> pin(item) }
            bindPinned(pin)

            add(R.string.share, R.drawable.share) { share() }
            add(R.string.labels, R.drawable.label) { label() }
            add(R.string.add_images, R.drawable.add_images) { selectImages() }
            add(R.string.attach_file, R.drawable.text_file) { selectFiles() }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(R.string.record_audio, R.drawable.record_audio) { checkAudioPermission() }
            }

            when (model.folder) {
                Folder.NOTES -> {
                    add(R.string.delete, R.drawable.delete) { delete() }
                    add(R.string.archive, R.drawable.archive) { archive() }
                }

                Folder.DELETED -> {
                    add(R.string.restore, R.drawable.restore) { restore() }
                    add(R.string.delete_forever, R.drawable.delete) { deleteForever() }
                }

                Folder.ARCHIVED -> {
                    add(R.string.delete, R.drawable.delete) { delete() }
                    add(R.string.unarchive, R.drawable.unarchive) { restore() }
                }
            }

            add(R.string.change_color, R.drawable.change_color) {
                showColorSelectDialog { selectedColor ->
                    model.color = selectedColor
                    setColor()
                }
            }
        }

        searchResultsAmount.mergeSkipFirst(searchResultPos).observe(this) { (amount, pos) ->
            val hasResults = amount > 0
            binding.SearchResults.text = if (hasResults) "${pos + 1}/$amount" else "0"
            searchPosNext.isEnabled = hasResults
            searchPosPrev.isEnabled = hasResults
        }

        searchResultPos.observeSkipFirst(this) { pos -> selectSearchResult(pos) }

        binding.EnterSearchKeyword.apply {
            doAfterTextChanged { text ->
                val amount = highlightSearchResults(text.toString())
                searchResultsAmount.value = amount
                if (amount > 0) {
                    searchResultPos.value = 0
                }
            }
        }
    }

    protected fun setSearchResultsAmount(amount: Int) {
        searchResultsAmount.value = amount
        if (searchResultPos.value >= amount) {
            searchResultPos.value = amount - 1
        }
    }

    /**
     * Visibly highlights found search results in the UI.
     *
     * @return amount of search results found
     */
    abstract fun highlightSearchResults(search: String): Int

    abstract fun selectSearchResult(resultPos: Int)

    private var navigationIconBeforeSearch: Drawable? = null

    protected fun startSearch() {
        binding.Toolbar.apply {
            menu.clear()
            searchPosNext =
                menu
                    .add(R.string.previous, R.drawable.arrow_upward) {
                        searchResultPos.apply {
                            if (value > 0) {
                                value -= 1
                            } else {
                                value = searchResultsAmount.value - 1
                            }
                        }
                    }
                    .setEnabled(false)
            searchPosPrev =
                menu
                    .add(R.string.next, R.drawable.arrow_downward) {
                        searchResultPos.apply {
                            if (value < searchResultsAmount.value - 1) {
                                value += 1
                            } else {
                                value = 0
                            }
                        }
                    }
                    .setEnabled(false)
            setNavigationOnClickListener { endSearch() }
            navigationIconBeforeSearch = navigationIcon
            setNavigationIcon(R.drawable.close)
        }
        binding.EnterSearchKeyword.apply {
            visibility = VISIBLE
            requestFocus()
            showKeyboard(this)
        }
        binding.SearchResults.apply {
            text = ""
            visibility = VISIBLE
        }
    }

    protected fun endSearch() {
        binding.EnterSearchKeyword.apply {
            visibility = GONE
            setText("")
        }
        binding.SearchResults.apply {
            visibility = GONE
            text = ""
        }
        setupToolbar()
        binding.Toolbar.navigationIcon = navigationIconBeforeSearch
    }

    abstract fun configureUI()

    open fun setupListeners() {
        binding.EnterTitle.initHistory(changeHistory) { text ->
            model.title = text.trim().toString()
        }
    }

    open fun setStateFromModel() {
        val (date, datePrefixResId) =
            when (preferences.notesSorting.value.sortedBy) {
                NotesSortBy.CREATION_DATE -> Pair(model.timestamp, R.string.creation_date)
                NotesSortBy.MODIFIED_DATE -> Pair(model.modifiedTimestamp, R.string.modified_date)
                else -> Pair(null, null)
            }
        binding.Date.displayFormattedTimestamp(date, preferences.dateFormat.value, datePrefixResId)
        binding.EnterTitle.setText(model.title)
        Operations.bindLabels(binding.LabelGroup, model.labels, model.textSize)

        setColor()
    }

    private fun handleSharedNote() {
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        val string = intent.getStringExtra(Intent.EXTRA_TEXT)
        val charSequence = intent.getCharSequenceExtra(Operations.extraCharSequence)
        val body = charSequence ?: string

        if (body != null) {
            model.body = Editable.Factory.getInstance().newEditable(body)
        }
        if (title != null) {
            model.title = title
        }
    }

    @RequiresApi(24)
    private fun checkAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_audio)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
                    }
                    .show()
            } else requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
        } else recordAudio()
    }

    private fun recordAudio() {
        if (model.audioRoot != null) {
            val intent = Intent(this, RecordAudioActivity::class.java)
            recordAudioActivityResultLauncher.launch(intent)
        } else Toast.makeText(this, R.string.insert_an_sd_card_audio, Toast.LENGTH_LONG).show()
    }

    private fun handleRejection() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.to_record_audio)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
            .show()
    }

    private fun selectImages() {
        if (model.imageRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            addImagesActivityResultLauncher.launch(intent)
        } else Toast.makeText(this, R.string.insert_an_sd_card_images, Toast.LENGTH_LONG).show()
    }

    private fun selectFiles() {
        if (model.filesRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            attachFilesActivityResultLauncher.launch(intent)
        } else Toast.makeText(this, R.string.insert_an_sd_card_files, Toast.LENGTH_LONG).show()
    }

    private fun share() {
        val body =
            when (type) {
                Type.NOTE -> model.body
                Type.LIST -> Operations.getBody(model.items.toMutableList())
            }
        Operations.shareNote(this, model.title, body)
    }

    private fun label() {
        val intent = Intent(this, SelectLabelsActivity::class.java)
        intent.putStringArrayListExtra(SelectLabelsActivity.SELECTED_LABELS, model.labels)
        selectLabelsActivityResultLauncher.launch(intent)
    }

    private fun delete() {
        moveNote(Folder.DELETED)
    }

    private fun restore() {
        moveNote(Folder.NOTES)
    }

    private fun archive() {
        moveNote(Folder.ARCHIVED)
    }

    private fun moveNote(toFolder: Folder) {
        val resultIntent =
            Intent().apply {
                putExtra(NOTE_ID, model.id)
                putExtra(FOLDER_FROM, model.folder.name)
                putExtra(FOLDER_TO, toFolder.name)
            }
        model.folder = toFolder
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_note_forever)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    model.deleteBaseNote()
                    super.finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pin(item: MenuItem) {
        model.pinned = !model.pinned
        bindPinned(item)
    }

    private fun setupImages() {
        val imageAdapter =
            PreviewImageAdapter(model.imageRoot) { position ->
                val intent =
                    Intent(this, ViewImageActivity::class.java).apply {
                        putExtra(ViewImageActivity.POSITION, position)
                        putExtra(Constants.SelectedBaseNote, model.id)
                    }
                viewImagesActivityResultLauncher.launch(intent)
            }

        imageAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {

                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    binding.ImagePreview.scrollToPosition(positionStart)
                    binding.ImagePreviewPosition.text =
                        "${positionStart + 1}/${imageAdapter.itemCount}"
                }
            }
        )
        binding.ImagePreview.apply {
            setHasFixedSize(true)
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(this@EditActivity, RecyclerView.HORIZONTAL, false)

            val pagerSnapHelper = PagerSnapHelper()
            pagerSnapHelper.attachToRecyclerView(this)
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val snappedView = pagerSnapHelper.findSnapView(layoutManager)
                            if (snappedView != null) {
                                val position = recyclerView.getChildAdapterPosition(snappedView)
                                binding.ImagePreviewPosition.text =
                                    "${position + 1}/${imageAdapter.itemCount}"
                            }
                        }
                    }
                }
            )
        }

        model.images.observe(this) { list ->
            imageAdapter.submitList(list)
            binding.ImagePreview.isVisible = list.isNotEmpty()
            binding.ImagePreviewPosition.isVisible = list.size > 1
        }
    }

    private fun setupFiles() {
        val fileAdapter =
            PreviewFileAdapter({ fileAttachment ->
                if (model.filesRoot == null) {
                    return@PreviewFileAdapter
                }
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        val file = File(model.filesRoot, fileAttachment.localName)
                        val uri = this@EditActivity.getUriForFile(file)
                        setDataAndType(uri, fileAttachment.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent.createChooser(intent, null))
                }
            }) { fileAttachment ->
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.delete_file, fileAttachment.originalName))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        model.deleteFiles(arrayListOf(fileAttachment))
                    }
                    .show()
                return@PreviewFileAdapter true
            }

        binding.FilesPreview.apply {
            setHasFixedSize(true)
            adapter = fileAdapter
            layoutManager =
                LinearLayoutManager(this@EditActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        model.files.observe(this) { list ->
            fileAdapter.submitList(list)
            val visible = list.isNotEmpty()
            binding.FilesPreview.apply {
                isVisible = visible
                if (visible) {
                    post {
                        scrollToPosition(fileAdapter.itemCount)
                        requestLayout()
                    }
                }
            }
        }
    }

    private fun displayFileErrors(errors: List<FileError>) {
        val recyclerView =
            RecyclerView(this).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                adapter = ErrorAdapter(errors)
                layoutManager = LinearLayoutManager(this@EditActivity)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    scrollIndicators = View.SCROLL_INDICATOR_TOP or View.SCROLL_INDICATOR_BOTTOM
                }
            }

        val message =
            if (errors.isNotEmpty() && errors[0].fileType == NotallyModel.FileType.IMAGE) {
                R.plurals.cant_add_images
            } else {
                R.plurals.cant_add_files
            }
        val title = getQuantityString(message, errors.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(recyclerView)
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show()
    }

    private fun setupAudios() {
        val adapter = AudioAdapter { position: Int ->
            if (position != -1) {
                val audio = model.audios.value[position]
                val intent = Intent(this, PlayAudioActivity::class.java)
                intent.putExtra(PlayAudioActivity.AUDIO, audio)
                playAudioActivityResultLauncher.launch(intent)
            }
        }
        binding.AudioRecyclerView.adapter = adapter

        model.audios.observe(this) { list ->
            adapter.submitList(list)
            binding.AudioHeader.isVisible = list.isNotEmpty()
            binding.AudioRecyclerView.isVisible = list.isNotEmpty()
        }
    }

    private fun setColor() {
        val color = Operations.extractColor(model.color, this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor = color
            window.navigationBarColor = color
        }
        binding.root.setBackgroundColor(color)
        binding.RecyclerView.setBackgroundColor(color)
        binding.RecyclerView.adapter?.notifyDataSetChanged()
        binding.Toolbar.backgroundTintList = ColorStateList.valueOf(color)
    }

    private fun initialiseBinding() {
        binding = ActivityEditBinding.inflate(layoutInflater)
        when (type) {
            Type.NOTE -> {
                binding.AddItem.visibility = View.GONE
                binding.RecyclerView.visibility = View.GONE
            }
            Type.LIST -> {
                binding.EnterBody.visibility = View.GONE
            }
        }

        val title = model.textSize.editTitleSize
        val date = model.textSize.displayBodySize
        val body = model.textSize.editBodySize

        binding.EnterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.EnterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        setupImages()
        setupFiles()
        setupAudios()
        model.addingFiles.setupProgressDialog(this, R.string.adding_files)
        model.eventBus.observe(this) { event ->
            event.handle { errors -> displayFileErrors(errors) }
        }

        binding.root.isSaveFromParentEnabled = false
    }

    private fun bindPinned(item: MenuItem) {
        val icon: Int
        val title: Int
        if (model.pinned) {
            icon = R.drawable.unpin
            title = R.string.unpin
        } else {
            icon = R.drawable.pin
            title = R.string.pin
        }
        item.setTitle(title)
        item.setIcon(icon)
    }

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 36

        const val NOTE_ID = "note_id"
        const val FOLDER_FROM = "folder_from"
        const val FOLDER_TO = "folder_to"
    }
}
