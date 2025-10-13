package com.philkes.notallyx.presentation.activity.note

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.VISIBLE
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Audio
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.data.model.isImageMimeType
import com.philkes.notallyx.databinding.ActivityEditBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.main.MainActivity
import com.philkes.notallyx.presentation.activity.main.MainActivity.Companion.EXTRA_FRAGMENT_TO_OPEN
import com.philkes.notallyx.presentation.activity.main.MainActivity.Companion.EXTRA_SKIP_START_VIEW_ON_BACK
import com.philkes.notallyx.presentation.activity.main.fragment.DisplayLabelFragment.Companion.EXTRA_DISPLAYED_LABEL
import com.philkes.notallyx.presentation.activity.note.SelectLabelsActivity.Companion.EXTRA_SELECTED_LABELS
import com.philkes.notallyx.presentation.activity.note.reminders.RemindersActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.addFastScroll
import com.philkes.notallyx.presentation.addIconButton
import com.philkes.notallyx.presentation.bindLabels
import com.philkes.notallyx.presentation.displayEditLabelDialog
import com.philkes.notallyx.presentation.displayFormattedTimestamp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.hideKeyboard
import com.philkes.notallyx.presentation.isLightColor
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setLightStatusAndNavBar
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.showKeyboard
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.note.ErrorAdapter
import com.philkes.notallyx.presentation.view.note.action.Action
import com.philkes.notallyx.presentation.view.note.action.AddActions
import com.philkes.notallyx.presentation.view.note.action.AddBottomSheet
import com.philkes.notallyx.presentation.view.note.action.MoreActions
import com.philkes.notallyx.presentation.view.note.action.MoreNoteBottomSheet
import com.philkes.notallyx.presentation.view.note.audio.AudioAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewFileAdapter
import com.philkes.notallyx.presentation.view.note.preview.PreviewImageAdapter
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.NotallyModel
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.widget.WidgetProvider
import com.philkes.notallyx.utils.FileError
import com.philkes.notallyx.utils.backup.exportNotes
import com.philkes.notallyx.utils.changeStatusAndNavigationBarColor
import com.philkes.notallyx.utils.changehistory.ChangeHistory
import com.philkes.notallyx.utils.getFileName
import com.philkes.notallyx.utils.getMimeType
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.log
import com.philkes.notallyx.utils.mergeSkipFirst
import com.philkes.notallyx.utils.observeSkipFirst
import com.philkes.notallyx.utils.shareNote
import com.philkes.notallyx.utils.showColorSelectDialog
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

abstract class EditActivity(private val type: Type) :
    LockedActivity<ActivityEditBinding>(), AddActions, MoreActions {
    private lateinit var audioAdapter: AudioAdapter
    private lateinit var fileAdapter: PreviewFileAdapter
    private lateinit var recordAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var addImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewImagesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var selectLabelsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var playAudioActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var attachFilesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportNotesActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var pinMenuItem: MenuItem
    protected var search = Search()

    internal val notallyModel: NotallyModel by viewModels()

    internal lateinit var changeHistory: ChangeHistory
    protected var undo: View? = null
    protected var redo: View? = null

    protected var colorInt: Int = -1
    protected var inputMethodManager: InputMethodManager? = null

    protected lateinit var toggleViewMode: ImageButton
    protected val canEdit
        get() = notallyModel.viewMode.value == NoteViewMode.EDIT

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable {
        lifecycleScope.launch(Dispatchers.Main) {
            updateModel()
            if (notallyModel.isModified()) {
                Log.d(TAG, "Auto-saving note...")
                saveNote(checkAutoSave = false)
            }
        }
    }

    override fun finish() {
        lifecycleScope.launch(Dispatchers.Main) {
            checkSave()
            super.finish()
        }
    }

    protected open suspend fun checkSave() {
        if (notallyModel.isEmpty()) {
            notallyModel.deleteBaseNote(checkAutoSave = false)
        } else if (notallyModel.isModified()) {
            saveNote()
        } else {
            notallyModel.checkBackupOnSave()
        }
    }

    protected open fun updateModel() {
        notallyModel.modifiedTimestamp = System.currentTimeMillis()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("id", notallyModel.id)
        if (notallyModel.isModified()) {
            lifecycleScope.launch { saveNote() }
        }
    }

    open suspend fun saveNote(checkAutoSave: Boolean = true) {
        updateModel()
        notallyModel.saveNote(checkAutoSave)
        WidgetProvider.sendBroadcast(application, longArrayOf(notallyModel.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputMethodManager =
            ContextCompat.getSystemService(baseContext, InputMethodManager::class.java)
        notallyModel.type = type
        initialiseBinding()
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        initChangeHistory()
        lifecycleScope.launch {
            val persistedId = savedInstanceState?.getLong("id")
            val selectedId = intent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0L)
            val id = persistedId ?: selectedId
            if (persistedId == null || notallyModel.originalNote == null) {
                notallyModel.setState(id, intent.data == null)
            }
            if (notallyModel.isNewNote) {
                when (intent.action) {
                    Intent.ACTION_SEND,
                    Intent.ACTION_SEND_MULTIPLE -> handleSharedNote()
                    Intent.ACTION_VIEW -> handleViewNote()
                    else ->
                        intent.getStringExtra(EXTRA_DISPLAYED_LABEL)?.let {
                            notallyModel.setLabels(listOf(it))
                        }
                }
            }

            setupToolbars()
            setupListeners()
            setStateFromModel(savedInstanceState)

            configureUI()
            binding.ScrollView.apply {
                visibility = View.VISIBLE
                addFastScroll(this@EditActivity)
            }
        }

        setupActivityResultLaunchers()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                updateModel()
                runBlocking(Dispatchers.IO) { saveNote() }
            } catch (e: Exception) {
                log(TAG, msg = "Saving note on Crash failed", throwable = e)
            } finally {
                // Let the system handle the crash
                DEFAULT_EXCEPTION_HANDLER?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContentLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime()) // For keyboard

            // Apply top padding to your main content to push it below the status bar
            // and bottom padding to push it above the navigation bar / keyboard / BottomAppBar
            view.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                // Combine navigation bar inset with keyboard inset for bottom padding
                // and ensure there's enough space for the BottomAppBar
                systemBarsInsets.bottom + imeInsets.bottom,
            )

            // Let the BottomAppBar consume its own insets
            // CoordinatorLayout usually handles this well with its children having specific
            // behaviors.
            // If the BottomAppBar isn't adapting, you might need a custom Behavior or ensure its
            // height is consistent.
            // A common approach is to set the bottom margin of your main content
            // to the height of the BottomAppBar + the nav bar inset.
            val navBarBottom = systemBarsInsets.bottom

            // Option A: Set padding on the ScrollView inside main_content_layout
            // This is often more effective for scrollable content.
            binding.ScrollView.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    binding.BottomAppBarLayout.height +
                        navBarBottom, // Space for BottomAppBar + nav bar
                )
            }
            insets
        }
    }

    open fun toggleCanEdit(mode: NoteViewMode) {
        binding.EnterTitle.apply {
            if (isFocused) {
                when {
                    mode == NoteViewMode.EDIT -> showKeyboard(this)
                    else -> hideKeyboard(this)
                }
            }
            setCanEdit(mode == NoteViewMode.EDIT)
        }
    }

    override fun onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        super.onDestroy()
    }

    protected fun resetIdleTimer() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        val idleTime = preferences.autoSaveAfterIdleTime.value
        if (idleTime > -1) {
            autoSaveHandler.postDelayed(autoSaveRunnable, idleTime.toLong() * 1000)
        }
    }

    private fun setupActivityResultLaunchers() {
        recordAudioActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    notallyModel.addAudio()
                }
            }
        addImagesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    val clipData = result.data?.clipData
                    if (uri != null) {
                        val uris = arrayOf(uri)
                        notallyModel.addImages(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        notallyModel.addImages(uris)
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
                                ViewImageActivity.EXTRA_DELETED_IMAGES,
                                FileAttachment::class.java,
                            )
                        }
                    if (!list.isNullOrEmpty()) {
                        notallyModel.deleteImages(list)
                    }
                }
            }
        selectLabelsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val list = result.data?.getStringArrayListExtra(EXTRA_SELECTED_LABELS)
                    if (list != null && list != notallyModel.labels) {
                        notallyModel.setLabels(list)
                        binding.LabelGroup.bindLabels(
                            notallyModel.labels,
                            notallyModel.textSize,
                            paddingTop = true,
                            colorInt,
                        )
                        resetIdleTimer()
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
                                PlayAudioActivity.EXTRA_AUDIO,
                                Audio::class.java,
                            )
                        }
                    if (audio != null) {
                        notallyModel.deleteAudio(audio)
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
                        notallyModel.addFiles(uris)
                    } else if (clipData != null) {
                        val uris =
                            Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                        notallyModel.addFiles(uris)
                    }
                }
            }

        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> baseModel.exportSelectedFileToUri(uri) }
                }
            }
        exportNotesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        baseModel.exportNotesToFolder(uri, listOf(notallyModel.getBaseNote()))
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
                    startRecordAudioActivity()
                } else handleRejection()
            }
        }
    }

    protected open fun initChangeHistory() {
        changeHistory =
            ChangeHistory().apply {
                canUndo.observe(this@EditActivity) { canUndo -> undo?.isEnabled = canUndo }
                canRedo.observe(this@EditActivity) { canRedo -> redo?.isEnabled = canRedo }
                stackPointer.observe(this@EditActivity) { _ -> resetIdleTimer() }
            }
    }

    protected open fun setupToolbars() {
        binding.Toolbar.setNavigationOnClickListener { finish() }
        binding.Toolbar.menu.apply {
            clear() // TODO: needed?
            add(R.string.search, R.drawable.search, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                startSearch()
            }
            pinMenuItem =
                add(R.string.pin, R.drawable.pin, MenuItem.SHOW_AS_ACTION_ALWAYS) { pin() }
            bindPinned()
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
                this@EditActivity.search.query = text.toString()
                updateSearchResults(this@EditActivity.search.query)
            }
        }
        initBottomMenu()
    }

    protected fun updateSearchResults(query: String) {
        val amountBefore = search.results.value
        val amount = highlightSearchResults(query)
        this.search.results.value = amount
        if (amount > 0) {
            search.resultPos.value =
                when {
                    amountBefore < 1 -> 0
                    search.resultPos.value >= amount -> amount - 1
                    else -> search.resultPos.value
                }
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
            search.nextMenuItem =
                menu
                    .add(R.string.previous, R.drawable.arrow_upward) {
                        search.resultPos.apply {
                            if (value > 0) {
                                value -= 1
                            } else {
                                value = search.results.value - 1
                            }
                        }
                    }
                    .setEnabled(false)
            search.prevMenuItem =
                menu
                    .add(R.string.next, R.drawable.arrow_downward) {
                        search.resultPos.apply {
                            if (value < search.results.value - 1) {
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
            setControlsContrastColorForAllViews(colorInt, overwriteBackground = false)
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

    protected fun isInSearchMode(): Boolean = binding.EnterSearchKeyword.visibility == VISIBLE

    protected fun endSearch() {
        binding.EnterSearchKeyword.apply {
            visibility = GONE
            setText("")
        }
        binding.SearchResults.apply {
            visibility = GONE
            text = ""
        }
        setupToolbars()
        binding.Toolbar.navigationIcon = navigationIconBeforeSearch
        binding.Toolbar.setControlsContrastColorForAllViews(colorInt, overwriteBackground = false)
    }

    protected open fun initBottomMenu() {
        binding.BottomAppBarLeft.apply {
            removeAllViews()
            addIconButton(R.string.adding_files, R.drawable.add, marginStart = 0) {
                AddBottomSheet(this@EditActivity, colorInt)
                    .show(supportFragmentManager, AddBottomSheet.TAG)
            }
        }
        binding.BottomAppBarCenter.apply {
            removeAllViews()
            undo =
                addIconButton(
                        R.string.undo,
                        R.drawable.undo,
                        marginStart = 2,
                        onLongClick = {
                            try {
                                changeHistory.undoAll()
                            } catch (e: ChangeHistory.ChangeHistoryException) {
                                application.log(TAG, throwable = e)
                            }
                            true
                        },
                    ) {
                        try {
                            changeHistory.undo()
                        } catch (e: ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canUndo.value }

            redo =
                addIconButton(
                        R.string.redo,
                        R.drawable.redo,
                        marginStart = 2,
                        onLongClick = {
                            try {
                                changeHistory.redoAll()
                            } catch (e: ChangeHistory.ChangeHistoryException) {
                                application.log(TAG, throwable = e)
                            }
                            true
                        },
                    ) {
                        try {
                            changeHistory.redo()
                        } catch (e: ChangeHistory.ChangeHistoryException) {
                            application.log(TAG, throwable = e)
                        }
                    }
                    .apply { isEnabled = changeHistory.canRedo.value }
        }
        binding.BottomAppBarRight.apply {
            removeAllViews()

            addToggleViewMode()
            addIconButton(R.string.tap_for_more_options, R.drawable.more_vert, marginStart = 0) {
                MoreNoteBottomSheet(
                        this@EditActivity,
                        createNoteTypeActions() + createFolderActions(),
                        colorInt,
                    )
                    .show(supportFragmentManager, MoreNoteBottomSheet.TAG)
            }
        }
        setBottomAppBarColor(colorInt)
    }

    protected fun ViewGroup.addToggleViewMode() {
        toggleViewMode =
            addIconButton(R.string.edit, R.drawable.visibility) {
                notallyModel.viewMode.value =
                    when (notallyModel.viewMode.value) {
                        NoteViewMode.EDIT -> NoteViewMode.READ_ONLY
                        NoteViewMode.READ_ONLY -> NoteViewMode.EDIT
                    }
            }
    }

    protected fun createFolderActions() =
        when (notallyModel.folder) {
            Folder.NOTES ->
                listOf(
                    Action(R.string.archive, R.drawable.archive) { _ ->
                        archive()
                        true
                    },
                    Action(R.string.delete, R.drawable.delete) { _ ->
                        delete()
                        true
                    },
                )

            Folder.DELETED ->
                listOf(
                    Action(R.string.delete_forever, R.drawable.delete) { _ ->
                        deleteForever()
                        true
                    },
                    Action(R.string.restore, R.drawable.restore) { _ ->
                        restore()
                        true
                    },
                )

            Folder.ARCHIVED ->
                listOf(
                    Action(R.string.delete, R.drawable.delete) { _ ->
                        delete()
                        true
                    },
                    Action(R.string.unarchive, R.drawable.unarchive) { _ ->
                        restore()
                        true
                    },
                )
        }

    protected fun createNoteTypeActions() =
        when (notallyModel.type) {
            Type.NOTE ->
                listOf(
                    Action(R.string.convert_to_list_note, R.drawable.convert_to_text) { _ ->
                        convertTo(Type.LIST)
                        true
                    }
                )
            Type.LIST ->
                listOf(
                    Action(R.string.convert_to_text_note, R.drawable.convert_to_text) { _ ->
                        convertTo(Type.NOTE)
                        true
                    }
                )
        }

    private fun convertTo(type: Type) {
        updateModel()
        lifecycleScope.launch {
            notallyModel.convertTo(type)
            val intent =
                Intent(
                    this@EditActivity,
                    when (type) {
                        Type.NOTE -> EditNoteActivity::class.java
                        Type.LIST -> EditListActivity::class.java
                    },
                )
            intent.putExtra(EXTRA_SELECTED_BASE_NOTE, notallyModel.id)
            startActivity(intent)
            finish()
        }
    }

    abstract fun configureUI()

    open fun setupListeners() {
        binding.EnterTitle.initHistory(changeHistory) { text ->
            notallyModel.title = text.trim().toString()
        }
        notallyModel.viewMode.observe(this) { value ->
            toggleViewMode.apply {
                setImageResource(
                    when (value) {
                        NoteViewMode.READ_ONLY -> R.drawable.edit
                        else -> R.drawable.visibility
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tooltipText =
                        getString(
                            when (value) {
                                NoteViewMode.READ_ONLY -> R.string.edit
                                else -> R.string.read_only
                            }
                        )
                }
            }
            value?.let { toggleCanEdit(it) }
        }
    }

    open fun setStateFromModel(savedInstanceState: Bundle?) {
        val (date, datePrefixResId) =
            when (preferences.notesSorting.value.sortedBy) {
                NotesSortBy.CREATION_DATE -> Pair(notallyModel.timestamp, R.string.creation_date)
                NotesSortBy.MODIFIED_DATE ->
                    Pair(notallyModel.modifiedTimestamp, R.string.modified_date)
                else -> Pair(null, null)
            }
        val dateFormat =
            if (preferences.applyDateFormatInNoteView.value) {
                preferences.dateFormat.value
            } else DateFormat.ABSOLUTE
        binding.Date.displayFormattedTimestamp(date, dateFormat, datePrefixResId)
        binding.EnterTitle.setText(notallyModel.title)
        bindLabels()
        setColor()
    }

    private fun bindLabels() {
        binding.LabelGroup.bindLabels(
            notallyModel.labels,
            notallyModel.textSize,
            paddingTop = true,
            colorInt,
            onClick = { label ->
                val bundle = Bundle()
                bundle.putString(EXTRA_DISPLAYED_LABEL, label)
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(EXTRA_FRAGMENT_TO_OPEN, R.id.DisplayLabel)
                        putExtra(EXTRA_DISPLAYED_LABEL, label)
                        putExtra(EXTRA_SKIP_START_VIEW_ON_BACK, true)
                    }
                )
            },
            onLongClick = { label ->
                displayEditLabelDialog(label, baseModel) { oldLabel, newLabel ->
                    notallyModel.labels.apply {
                        remove(oldLabel)
                        add(newLabel)
                    }
                    bindLabels()
                }
            },
        )
    }

    private fun handleSharedNote() {
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val string = intent.getStringExtra(Intent.EXTRA_TEXT)
        val files =
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { listOf(it) }
        if (string != null) {
            notallyModel.body = Editable.Factory.getInstance().newEditable(string)
        }
        if (title != null) {
            notallyModel.title = title
        }
        files?.let {
            val filesByType =
                it.groupBy { uri ->
                    getMimeType(uri)?.let { mimeType ->
                        if (mimeType.isImageMimeType) {
                            NotallyModel.FileType.IMAGE
                        } else {
                            NotallyModel.FileType.ANY
                        }
                    } ?: NotallyModel.FileType.ANY
                }
            filesByType[NotallyModel.FileType.IMAGE]?.let { images ->
                notallyModel.addImages(images.toTypedArray())
            }
            filesByType[NotallyModel.FileType.ANY]?.let { otherFiles ->
                notallyModel.addFiles(otherFiles.toTypedArray())
            }
        }
    }

    private fun handleViewNote() {
        val text =
            intent.data?.let { uri ->
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                }
                    ?: run {
                        showToast(R.string.cant_load_file)
                        null
                    }
            } ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        val title =
            intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.data?.let { getFileName(it) }
        if (text != null) {
            notallyModel.body = Editable.Factory.getInstance().newEditable(text)
        }
        if (title != null) {
            notallyModel.title = title
        }
    }

    @RequiresApi(24)
    override fun recordAudio() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(permission)) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.please_grant_notally_audio)
                    .setCancelButton()
                    .setPositiveButton(R.string.continue_) { _, _ ->
                        requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
                    }
                    .show()
            } else requestPermissions(arrayOf(permission), REQUEST_AUDIO_PERMISSION)
        } else startRecordAudioActivity()
    }

    private fun startRecordAudioActivity() {
        if (notallyModel.audioRoot != null) {
            val intent = Intent(this, RecordAudioActivity::class.java)
            recordAudioActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_audio)
    }

    private fun handleRejection() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.to_record_audio)
            .setCancelButton()
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            }
            .show()
    }

    override fun addImages() {
        if (notallyModel.imageRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT)
                    .apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    .wrapWithChooser(this)
            addImagesActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_images)
    }

    override fun attachFiles() {
        if (notallyModel.filesRoot != null) {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT)
                    .apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    .wrapWithChooser(this)
            attachFilesActivityResultLauncher.launch(intent)
        } else showToast(R.string.insert_an_sd_card_files)
    }

    override fun changeColor() {
        lifecycleScope.launch {
            val colors =
                withContext(Dispatchers.IO) {
                        NotallyDatabase.getDatabase(this@EditActivity, observePreferences = false)
                            .value
                            .getBaseNoteDao()
                            .getAllColors()
                    }
                    .toMutableList()
            if (colors.none { it == notallyModel.color }) {
                colors.add(notallyModel.color)
            }
            showColorSelectDialog(
                colors,
                notallyModel.color,
                colorInt.isLightColor(),
                { selectedColor, oldColor ->
                    if (oldColor != null) {
                        baseModel.changeColor(oldColor, selectedColor)
                    }
                    notallyModel.color = selectedColor
                    setColor()
                    resetIdleTimer()
                },
            ) { colorToDelete, newColor ->
                baseModel.changeColor(colorToDelete, newColor)
                if (colorToDelete == notallyModel.color) {
                    notallyModel.color = newColor
                    setColor()
                }
                resetIdleTimer()
            }
        }
    }

    override fun changeReminders() {
        val intent = Intent(this, RemindersActivity::class.java)
        intent.putExtra(RemindersActivity.NOTE_ID, notallyModel.id)
        startActivity(intent)
    }

    override fun changeLabels() {
        val intent = Intent(this, SelectLabelsActivity::class.java)
        intent.putStringArrayListExtra(EXTRA_SELECTED_LABELS, notallyModel.labels)
        selectLabelsActivityResultLauncher.launch(intent)
    }

    override fun share() {
        this.shareNote(notallyModel.getBaseNote())
    }

    override fun export(mimeType: ExportMimeType) {
        exportNotes(
            mimeType,
            listOf(notallyModel.getBaseNote()),
            exportFileActivityResultLauncher,
            exportNotesActivityResultLauncher,
        )
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
                putExtra(EXTRA_NOTE_ID, notallyModel.id)
                putExtra(EXTRA_FOLDER_FROM, notallyModel.folder.name)
                putExtra(EXTRA_FOLDER_TO, toFolder.name)
            }
        notallyModel.folder = toFolder
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_note_forever)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    notallyModel.deleteBaseNote()
                    super.finish()
                }
            }
            .setCancelButton()
            .show()
    }

    fun pin() {
        notallyModel.pinned = !notallyModel.pinned
        bindPinned()
    }

    private fun setupImages() {
        val imageAdapter =
            PreviewImageAdapter(notallyModel.imageRoot) { position ->
                val intent =
                    Intent(this, ViewImageActivity::class.java).apply {
                        putExtra(ViewImageActivity.EXTRA_POSITION, position)
                        putExtra(EXTRA_SELECTED_BASE_NOTE, notallyModel.id)
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

        notallyModel.images.observe(this) { list ->
            imageAdapter.submitList(list)
            binding.ImagePreview.isVisible = list.isNotEmpty()
            binding.ImagePreviewPosition.isVisible = list.size > 1
        }
    }

    private fun setupFiles() {
        fileAdapter =
            PreviewFileAdapter({ fileAttachment ->
                if (notallyModel.filesRoot == null) {
                    return@PreviewFileAdapter
                }
                val intent =
                    Intent(Intent.ACTION_VIEW)
                        .apply {
                            val file = File(notallyModel.filesRoot, fileAttachment.localName)
                            val uri = this@EditActivity.getUriForFile(file)
                            setDataAndType(uri, fileAttachment.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        .wrapWithChooser(this@EditActivity)
                startActivity(intent)
            }) { fileAttachment ->
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.delete_file, fileAttachment.originalName))
                    .setCancelButton()
                    .setPositiveButton(R.string.delete) { _, _ ->
                        notallyModel.deleteFiles(arrayListOf(fileAttachment))
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
        notallyModel.files.observe(this) { list ->
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
            .setCancelButton()
            .setCancelable(false)
            .show()
    }

    private fun setupAudios() {
        audioAdapter = AudioAdapter { position: Int ->
            if (position != -1) {
                val audio = notallyModel.audios.value[position]
                val intent = Intent(this, PlayAudioActivity::class.java)
                intent.putExtra(PlayAudioActivity.EXTRA_AUDIO, audio)
                playAudioActivityResultLauncher.launch(intent)
            }
        }
        binding.AudioRecyclerView.adapter = audioAdapter

        notallyModel.audios.observe(this) { list ->
            audioAdapter.submitList(list)
            binding.AudioHeader.isVisible = list.isNotEmpty()
            binding.AudioRecyclerView.isVisible = list.isNotEmpty()
        }
    }

    protected open fun setColor() {
        colorInt = extractColor(notallyModel.color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            changeStatusAndNavigationBarColor(colorInt)
            window.setLightStatusAndNavBar(colorInt.isLightColor())
        }
        binding.apply {
            ScrollView.apply {
                setBackgroundColor(colorInt)
                setControlsContrastColorForAllViews(colorInt)
            }
            root.setBackgroundColor(colorInt)
            MainListView.setBackgroundColor(colorInt)
            CheckedListView.setBackgroundColor(colorInt)
            Toolbar.backgroundTintList = ColorStateList.valueOf(colorInt)
            Toolbar.setControlsContrastColorForAllViews(colorInt)
        }
        setBottomAppBarColor(colorInt)
        fileAdapter.setColor(colorInt)
        audioAdapter.setColor(colorInt)
    }

    protected fun setBottomAppBarColor(@ColorInt color: Int) {
        binding.apply {
            BottomAppBar.setBackgroundColor(color)
            BottomAppBar.setControlsContrastColorForAllViews(color)
            BottomAppBarLayout.backgroundTint = ColorStateList.valueOf(color)
        }
    }

    private fun initialiseBinding() {
        binding = ActivityEditBinding.inflate(layoutInflater)
        when (type) {
            Type.NOTE -> {
                binding.AddItem.visibility = GONE
                binding.MainListView.visibility = GONE
                binding.CheckedListView.visibility = GONE
            }
            Type.LIST -> {
                binding.EnterBody.visibility = GONE
                binding.CheckedListView.visibility =
                    if (preferences.listItemSorting.value == ListItemSort.AUTO_SORT_BY_CHECKED)
                        VISIBLE
                    else GONE
            }
        }

        val title = notallyModel.textSize.editTitleSize
        val date = notallyModel.textSize.displayBodySize
        val body = notallyModel.textSize.editBodySize

        binding.EnterTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, date)
        binding.EnterBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        setupImages()
        setupFiles()
        setupAudios()
        notallyModel.addingFiles.setupProgressDialog(this, R.string.adding_files)
        notallyModel.eventBus.observe(this) { event ->
            event.handle { errors -> displayFileErrors(errors) }
        }

        binding.root.isSaveFromParentEnabled = false
    }

    private fun bindPinned() {
        val icon: Int
        val title: Int
        if (notallyModel.pinned) {
            icon = R.drawable.unpin
            title = R.string.unpin
        } else {
            icon = R.drawable.pin
            title = R.string.pin
        }
        pinMenuItem.apply {
            setTitle(title)
            setIcon(icon)
        }
    }

    data class Search(
        var query: String = "",
        var prevMenuItem: MenuItem? = null,
        var nextMenuItem: MenuItem? = null,
        var resultPos: NotNullLiveData<Int> = NotNullLiveData(-1),
        var results: NotNullLiveData<Int> = NotNullLiveData(-1),
    )

    companion object {
        private const val TAG = "EditActivity"
        private const val REQUEST_AUDIO_PERMISSION = 36

        const val EXTRA_SELECTED_BASE_NOTE = "notallyx.intent.extra.SELECTED_BASE_NOTE"
        const val EXTRA_NOTE_ID = "notallyx.intent.extra.NOTE_ID"
        const val EXTRA_FOLDER_FROM = "notallyx.intent.extra.FOLDER_FROM"
        const val EXTRA_FOLDER_TO = "notallyx.intent.extra.FOLDER_TO"

        val DEFAULT_EXCEPTION_HANDLER = Thread.getDefaultUncaughtExceptionHandler()
    }
}
