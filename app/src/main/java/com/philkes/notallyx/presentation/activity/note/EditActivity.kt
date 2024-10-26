package com.philkes.notallyx.presentation.activity.note

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
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
import com.philkes.notallyx.databinding.DialogProgressBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.presentation.view.note.ErrorAdapter
import com.philkes.notallyx.presentation.view.note.audio.AudioAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewFileAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewImageAdapter
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.add
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.createTextWatcherWithHistory
import com.philkes.notallyx.utils.displayFormattedTimestamp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class EditActivity(private val type: Type) : LockedActivity<ActivityEditBinding>() {
    internal val model: NotallyModel by viewModels()
    internal lateinit var changeHistory: ChangeHistory
    internal lateinit var enterTitleTextWatcher: TextWatcher

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            saveNote()
            super.finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", model.id)
        lifecycleScope.launch { saveNote() }
    }

    open suspend fun saveNote() {
        if (changeHistory.canUndo()) {
            model.modifiedTimestamp = System.currentTimeMillis()
        }
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
            }

            setupToolbar()
            setupListeners()
            setStateFromModel()

            configureUI()
            binding.ScrollView.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_ADD_IMAGES -> {
                    val uri = data?.data
                    val clipData = data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        model.addImages(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        model.addImages(uris)
                    }
                }
                REQUEST_VIEW_IMAGES -> {
                    val list =
                        data?.getParcelableArrayListExtra<FileAttachment>(
                            ViewImageActivity.DELETED_IMAGES
                        )
                    if (!list.isNullOrEmpty()) {
                        model.deleteImages(list)
                    }
                }
                REQUEST_SELECT_LABELS -> {
                    val list = data?.getStringArrayListExtra(SelectLabelsActivity.SELECTED_LABELS)
                    if (list != null && list != model.labels) {
                        model.setLabels(list)
                        Operations.bindLabels(binding.LabelGroup, model.labels, model.textSize)
                    }
                }
                REQUEST_RECORD_AUDIO -> model.addAudio()
                REQUEST_PLAY_AUDIO -> {
                    val audio = data?.getParcelableExtra<Audio>(PlayAudioActivity.AUDIO)
                    if (audio != null) {
                        model.deleteAudio(audio)
                    }
                }
                REQUEST_ATTACH_FILES -> {
                    val uri = data?.data
                    val clipData = data?.clipData
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
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> selectImages()
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
    }

    protected open fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { finish() }

        binding.Toolbar.menu.apply {
            val pin =
                add(R.string.pin, R.drawable.pin, MenuItem.SHOW_AS_ACTION_ALWAYS) { item ->
                    pin(item)
                }
            bindPinned(pin)

            val undo =
                add(R.string.undo, R.drawable.undo, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                    changeHistory.undo()
                }
            val redo =
                add(R.string.redo, R.drawable.redo, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                    changeHistory.redo()
                }

            initActionManager(undo, redo)

            undo.isEnabled = changeHistory.canUndo()
            redo.isEnabled = changeHistory.canRedo()

            add(R.string.share, R.drawable.share) { share() }
            add(R.string.labels, R.drawable.label) { label() }
            add(R.string.add_images, R.drawable.add_images) {
                checkNotificationPermission { selectImages() }
            }
            add(R.string.attach_file, R.drawable.text_file) {
                checkNotificationPermission { selectFiles() }
            }

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
        }
    }

    abstract fun configureUI()

    open fun setupListeners() {
        enterTitleTextWatcher = run {
            binding.EnterTitle.createTextWatcherWithHistory(changeHistory) { text: Editable ->
                model.title = text.trim().toString()
            }
        }
        binding.EnterTitle.addTextChangedListener(enterTitleTextWatcher)
    }

    open fun setStateFromModel() {
        binding.DateCreated.displayFormattedTimestamp(model.timestamp, preferences.dateFormat.value)
        updateEnterTitle()
        Operations.bindLabels(binding.LabelGroup, model.labels, model.textSize)

        setColor()
    }

    private fun updateEnterTitle() {
        binding.EnterTitle.apply {
            removeTextChangedListener(enterTitleTextWatcher)
            setText(model.title)
            addTextChangedListener(enterTitleTextWatcher)
        }
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

    private fun checkNotificationPermission(onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(permission)) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.please_grant_notally_notification)
                        .setNegativeButton(R.string.cancel) { _, _ -> onSuccess() }
                        .setPositiveButton(R.string.continue_) { _, _ ->
                            requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
                        }
                        .setOnDismissListener { onSuccess() }
                        .show()
                } else requestPermissions(arrayOf(permission), REQUEST_NOTIFICATION_PERMISSION)
            } else onSuccess()
        } else onSuccess()
    }

    private fun recordAudio() {
        if (model.audioRoot != null) {
            val intent = Intent(this, RecordAudioActivity::class.java)
            startActivityForResult(intent, REQUEST_RECORD_AUDIO)
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
            startActivityForResult(intent, REQUEST_ADD_IMAGES)
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
            startActivityForResult(intent, REQUEST_ATTACH_FILES)
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
        startActivityForResult(intent, REQUEST_SELECT_LABELS)
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
                startActivityForResult(intent, REQUEST_VIEW_IMAGES)
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
                        val uri =
                            FileProvider.getUriForFile(
                                this@EditActivity,
                                "${packageName}.provider",
                                file,
                            )
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
        val title = resources.getQuantityString(message, errors.size, errors.size)
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
                startActivityForResult(intent, REQUEST_PLAY_AUDIO)
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
        }
        binding.root.setBackgroundColor(color)
        binding.RecyclerView.setBackgroundColor(color)
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

        val title = TextSize.getEditTitleSize(model.textSize)
        val date = TextSize.getDisplayBodySize(model.textSize)
        val body = TextSize.getEditBodySize(model.textSize)

        binding.EnterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.DateCreated.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.EnterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        setupImages()
        setupFiles()
        setupAudios()
        setupProgressDialog()

        binding.root.isSaveFromParentEnabled = false
    }

    private fun setupProgressDialog() {
        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        val dialog =
            MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.root)
                .setCancelable(false)
                .create()

        model.addingFiles.observe(this) { progress ->
            if (progress.inProgress) {
                dialog.setTitle(
                    if (progress.fileType == NotallyModel.FileType.IMAGE) {
                        R.string.adding_images
                    } else {
                        R.string.adding_files
                    }
                )
                dialog.show()
                dialogBinding.apply {
                    ProgressBar.max = progress.total
                    ProgressBar.setProgressCompat(progress.current, true)
                    Count.text = getString(R.string.count, progress.current, progress.total)
                }
            } else dialog.dismiss()
        }

        model.eventBus.observe(this) { event ->
            event.handle { errors -> displayFileErrors(errors) }
        }
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
        private const val REQUEST_ADD_IMAGES = 30
        private const val REQUEST_VIEW_IMAGES = 31
        private const val REQUEST_NOTIFICATION_PERMISSION = 32
        private const val REQUEST_SELECT_LABELS = 33
        private const val REQUEST_RECORD_AUDIO = 34
        private const val REQUEST_PLAY_AUDIO = 35
        private const val REQUEST_AUDIO_PERMISSION = 36
        private const val REQUEST_ATTACH_FILES = 37

        const val NOTE_ID = "note_id"
        const val FOLDER_FROM = "folder_from"
        const val FOLDER_TO = "folder_to"
    }
}
