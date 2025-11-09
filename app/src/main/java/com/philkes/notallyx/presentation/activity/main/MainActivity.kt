package com.philkes.notallyx.presentation.activity.main

import android.content.Intent
import android.os.Bundle
import android.transition.TransitionManager
import android.view.Menu
import android.view.Menu.CATEGORY_CONTAINER
import android.view.Menu.CATEGORY_SYSTEM
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFade
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.databinding.ActivityMainBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.main.fragment.DisplayLabelFragment.Companion.EXTRA_DISPLAYED_LABEL
import com.philkes.notallyx.presentation.activity.main.fragment.NotallyFragment
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.getQuantityString
import com.philkes.notallyx.presentation.movedToResId
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.view.misc.NotNullLiveData
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.TriStateCheckBox
import com.philkes.notallyx.presentation.view.misc.tristatecheckbox.setMultiChoiceTriStateItems
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel.Companion.CURRENT_LABEL_EMPTY
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel.Companion.CURRENT_LABEL_NONE
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_DEFAULT
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_UNLABELED
import com.philkes.notallyx.utils.backup.exportNotes
import com.philkes.notallyx.utils.shareNote
import com.philkes.notallyx.utils.showColorSelectDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : LockedActivity<ActivityMainBinding>() {

    private lateinit var navController: NavController
    private lateinit var configuration: AppBarConfiguration
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportNotesActivityResultLauncher: ActivityResultLauncher<Intent>

    private var isStartViewFragment = false
    private val actionModeCancelCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                baseModel.actionMode.close(true)
            }
        }

    var getCurrentFragmentNotes: (() -> Collection<BaseNote>?)? = null

    override fun onSupportNavigateUp(): Boolean {
        baseModel.keyword = ""
        return navController.navigateUp(configuration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.Toolbar)
        configureEdgeToEdgeInsets()

        setupFAB()
        setupMenu()
        setupActionMode()
        setupNavigation()

        setupActivityResultLaunchers()

        preferences.alwaysShowSearchBar.observe(this) { invalidateOptionsMenu() }

        val fragmentIdToLoad = intent.getIntExtra(EXTRA_FRAGMENT_TO_OPEN, -1)
        if (fragmentIdToLoad != -1) {
            navController.navigate(fragmentIdToLoad, intent.extras)
        } else if (savedInstanceState == null) {
            navigateToStartView()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (baseModel.actionMode.enabled.value) {
                        return
                    }
                    if (
                        !isStartViewFragment &&
                            !intent.getBooleanExtra(EXTRA_SKIP_START_VIEW_ON_BACK, false)
                    ) {
                        navigateToStartView()
                    } else {
                        finish()
                    }
                }
            },
        )
        onBackPressedDispatcher.addCallback(this, actionModeCancelCallback)

        baseModel.progress.setupProgressDialog(this)
    }

    private fun configureEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val navHostFragment = binding.NavHostFragment
        ViewCompat.setOnApplyWindowInsetsListener(binding.RelativeLayout) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Apply padding to the main content views
            // Top margin for the Toolbar to avoid being under the status bar
            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout()
            }

            binding.ActionMode.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout()
            }

            // Apply padding to the navigationview top header
            binding.NavigationView.getHeaderView(0).apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout()
            }

            // TakeNote FAB is at the very bottom
            binding.TakeNote.apply {
                val marginLayoutParams = layoutParams as ViewGroup.MarginLayoutParams
                marginLayoutParams.bottomMargin = 16.dp + systemBarsInsets.bottom + imeInsets.bottom
                marginLayoutParams.marginEnd = 16.dp
                requestLayout()
            }

            // The ActionMode toolbar's position will naturally be below the Toolbar,
            // so its top offset is handled by the Toolbar's adjustment.

            // The main content (NavHostFragment) needs bottom padding to avoid
            // being obscured by the system navigation bar and the keyboard.
            // If NavHostFragment contains a ScrollView/RecyclerView, you might apply
            // this padding to that scrollable view instead for better behavior.
            navHostFragment.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    systemBarsInsets.bottom + imeInsets.bottom,
                )
            }
            insets
        }
    }

    private fun getStartViewNavigation(): Pair<Int, Bundle> {
        return when (val startView = preferences.startView.value) {
            START_VIEW_DEFAULT -> Pair(R.id.Notes, Bundle())
            START_VIEW_UNLABELED -> Pair(R.id.Unlabeled, Bundle())
            else -> {
                val bundle = Bundle().apply { putString(EXTRA_DISPLAYED_LABEL, startView) }
                Pair(R.id.DisplayLabel, bundle)
            }
        }
    }

    private fun navigateToStartView() {
        val (id, bundle) = getStartViewNavigation()
        navController.navigate(id, bundle)
    }

    private fun setupFAB() {
        binding.TakeNote.setOnClickListener {
            val intent = Intent(this, EditNoteActivity::class.java)
            startActivity(prepareNewNoteIntent(intent))
        }
        binding.MakeList.setOnClickListener {
            val intent = Intent(this, EditListActivity::class.java)
            startActivity(prepareNewNoteIntent(intent))
        }
    }

    private fun prepareNewNoteIntent(intent: Intent): Intent {
        return supportFragmentManager
            .findFragmentById(R.id.NavHostFragment)
            ?.childFragmentManager
            ?.fragments
            ?.firstOrNull()
            ?.let { fragment ->
                return if (fragment is NotallyFragment) {
                    fragment.prepareNewNoteIntent(intent)
                } else intent
            } ?: intent
    }

    private var labelsMenuItems: List<MenuItem> = listOf()
    private var labelsMoreMenuItem: MenuItem? = null
    private var labels: List<String> = listOf()
    private var labelsLiveData: LiveData<List<String>>? = null

    private fun setupMenu() {
        binding.NavigationView.menu.apply {
            add(0, R.id.Notes, 0, R.string.notes).setCheckable(true).setIcon(R.drawable.home)

            addStaticLabelsMenuItems()
            NotallyDatabase.getDatabase(application).observe(this@MainActivity) { database ->
                labelsLiveData?.removeObservers(this@MainActivity)
                labelsLiveData =
                    database.getLabelDao().getAll().also {
                        it.observe(this@MainActivity) { labels ->
                            this@MainActivity.labels = labels
                            setupLabelsMenuItems(labels, preferences.maxLabels.value)
                        }
                    }
            }

            add(2, R.id.Deleted, CATEGORY_SYSTEM + 1, R.string.deleted)
                .setCheckable(true)
                .setIcon(R.drawable.delete)
            add(2, R.id.Archived, CATEGORY_SYSTEM + 2, R.string.archived)
                .setCheckable(true)
                .setIcon(R.drawable.archive)
            add(3, R.id.Reminders, CATEGORY_SYSTEM + 3, R.string.reminders)
                .setCheckable(true)
                .setIcon(R.drawable.notifications)
            add(3, R.id.Settings, CATEGORY_SYSTEM + 4, R.string.settings)
                .setCheckable(true)
                .setIcon(R.drawable.settings)
        }
        baseModel.preferences.labelsHidden.observe(this) { hiddenLabels ->
            hideLabelsInNavigation(hiddenLabels, baseModel.preferences.maxLabels.value)
        }
        baseModel.preferences.maxLabels.observe(this) { maxLabels ->
            binding.NavigationView.menu.setupLabelsMenuItems(labels, maxLabels)
        }
    }

    private fun Menu.addStaticLabelsMenuItems() {
        add(1, R.id.Unlabeled, CATEGORY_CONTAINER + 1, R.string.unlabeled)
            .setCheckable(true)
            .setChecked(baseModel.currentLabel == CURRENT_LABEL_NONE)
            .setIcon(R.drawable.label_off)
        add(1, R.id.Labels, CATEGORY_CONTAINER + 2, R.string.labels)
            .setCheckable(true)
            .setIcon(R.drawable.label_more)
    }

    private fun Menu.setupLabelsMenuItems(labels: List<String>, maxLabelsToDisplay: Int) {
        removeGroup(1)
        addStaticLabelsMenuItems()
        labelsMenuItems =
            labels
                .mapIndexed { index, label ->
                    add(1, R.id.DisplayLabel, CATEGORY_CONTAINER + index + 3, label)
                        .setCheckable(true)
                        .setChecked(baseModel.currentLabel == label)
                        .setVisible(index < maxLabelsToDisplay)
                        .setIcon(R.drawable.label)
                        .setOnMenuItemClickListener {
                            navigateToLabel(label)
                            false
                        }
                }
                .toList()

        labelsMoreMenuItem =
            if (labelsMenuItems.size > maxLabelsToDisplay) {
                add(
                        1,
                        R.id.Labels,
                        CATEGORY_CONTAINER + labelsMenuItems.size + 2,
                        getString(R.string.more, labelsMenuItems.size - maxLabelsToDisplay),
                    )
                    .setCheckable(true)
                    .setIcon(R.drawable.label)
            } else null
        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)
        hideLabelsInNavigation(baseModel.preferences.labelsHidden.value, maxLabelsToDisplay)
    }

    private fun navigateToLabel(label: String) {
        val bundle = Bundle().apply { putString(EXTRA_DISPLAYED_LABEL, label) }
        navController.navigate(R.id.DisplayLabel, bundle)
    }

    private fun hideLabelsInNavigation(hiddenLabels: Set<String>, maxLabelsToDisplay: Int) {
        var visibleLabels = 0
        labelsMenuItems.forEach { menuItem ->
            val visible =
                !hiddenLabels.contains(menuItem.title) && visibleLabels < maxLabelsToDisplay
            menuItem.setVisible(visible)
            if (visible) {
                visibleLabels++
            }
        }
        labelsMoreMenuItem?.setTitle(getString(R.string.more, labels.size - visibleLabels))
    }

    private fun setupActionMode() {
        binding.ActionMode.setNavigationOnClickListener { baseModel.actionMode.close(true) }

        val transition =
            MaterialFade().apply {
                secondaryAnimatorProvider = null
                excludeTarget(binding.NavHostFragment, true)
                excludeChildren(binding.NavHostFragment, true)
                excludeTarget(binding.TakeNote, true)
                excludeTarget(binding.MakeList, true)
                excludeTarget(binding.NavigationView, true)
            }

        baseModel.actionMode.enabled.observe(this) { enabled ->
            TransitionManager.beginDelayedTransition(binding.RelativeLayout, transition)
            if (enabled) {
                binding.Toolbar.visibility = View.GONE
                binding.ActionMode.visibility = View.VISIBLE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.Toolbar.visibility = View.VISIBLE
                binding.ActionMode.visibility = View.GONE
                binding.DrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
            }
            actionModeCancelCallback.isEnabled = enabled
        }

        val menu = binding.ActionMode.menu
        baseModel.folder.observe(this@MainActivity, ModelFolderObserver(menu, baseModel))
        baseModel.actionMode.loading.observe(this@MainActivity) { loading ->
            menu.setGroupEnabled(Menu.NONE, !loading)
        }
    }

    private fun moveNotes(folderTo: Folder) {
        if (baseModel.actionMode.loading.value || baseModel.actionMode.isEmpty()) {
            return
        }
        try {
            baseModel.actionMode.loading.value = true
            val folderFrom = baseModel.actionMode.getFirstNote().folder
            val ids = baseModel.moveBaseNotes(folderTo)
            Snackbar.make(
                    findViewById(R.id.DrawerLayout),
                    getQuantityString(folderTo.movedToResId(), ids.size),
                    Snackbar.LENGTH_SHORT,
                )
                .apply { setAction(R.string.undo) { baseModel.moveBaseNotes(ids, folderFrom) } }
                .show()
        } finally {
            baseModel.actionMode.loading.value = false
        }
    }

    private fun share() {
        val baseNote = baseModel.actionMode.getFirstNote()
        this.shareNote(baseNote)
    }

    private fun deleteForever() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_selected_notes)
            .setPositiveButton(R.string.delete) { _, _ -> baseModel.deleteSelectedBaseNotes() }
            .setCancelButton()
            .show()
    }

    private fun label() {
        val baseNotes = baseModel.actionMode.selectedNotes.values
        lifecycleScope.launch {
            val labels = baseModel.getAllLabels()
            if (labels.isNotEmpty()) {
                displaySelectLabelsDialog(labels, baseNotes)
            } else {
                baseModel.actionMode.close(true)
                navigateWithAnimation(R.id.Labels)
            }
        }
    }

    private fun displaySelectLabelsDialog(labels: Array<String>, baseNotes: Collection<BaseNote>) {
        val checkedPositions =
            labels
                .map { label ->
                    if (baseNotes.all { it.labels.contains(label) }) {
                        TriStateCheckBox.State.CHECKED
                    } else if (baseNotes.any { it.labels.contains(label) }) {
                        TriStateCheckBox.State.PARTIALLY_CHECKED
                    } else {
                        TriStateCheckBox.State.UNCHECKED
                    }
                }
                .toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.labels)
            .setCancelButton()
            .setMultiChoiceTriStateItems(this, labels, checkedPositions) { idx, state ->
                checkedPositions[idx] = state
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val checkedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.CHECKED) {
                            labels[index]
                        } else null
                    }
                val uncheckedLabels =
                    checkedPositions.mapIndexedNotNull { index, checked ->
                        if (checked == TriStateCheckBox.State.UNCHECKED) {
                            labels[index]
                        } else null
                    }
                val updatedBaseNotesLabels =
                    baseNotes.map { baseNote ->
                        val noteLabels = baseNote.labels.toMutableList()
                        checkedLabels.forEach { checkedLabel ->
                            if (!noteLabels.contains(checkedLabel)) {
                                noteLabels.add(checkedLabel)
                            }
                        }
                        uncheckedLabels.forEach { uncheckedLabel ->
                            if (noteLabels.contains(uncheckedLabel)) {
                                noteLabels.remove(uncheckedLabel)
                            }
                        }
                        noteLabels
                    }
                baseNotes.zip(updatedBaseNotesLabels).forEach { (baseNote, updatedLabels) ->
                    baseModel.updateBaseNoteLabels(updatedLabels, baseNote.id)
                }
            }
            .show()
    }

    private fun exportSelectedNotes(mimeType: ExportMimeType) {
        exportNotes(
            baseModel.actionMode.selectedNotes.values,
            mimeType,
            exportFileActivityResultLauncher,
            exportNotesActivityResultLauncher,
        )
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.NavHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        configuration = AppBarConfiguration(binding.NavigationView.menu, binding.DrawerLayout)
        setupActionBarWithNavController(navController, configuration)

        var fragmentIdToLoad: Int? = null
        binding.NavigationView.setNavigationItemSelectedListener { item ->
            fragmentIdToLoad = item.itemId
            binding.DrawerLayout.closeDrawer(GravityCompat.START)
            return@setNavigationItemSelectedListener true
        }

        binding.DrawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {

                override fun onDrawerClosed(drawerView: View) {
                    if (
                        fragmentIdToLoad != null &&
                            navController.currentDestination?.id != fragmentIdToLoad
                    ) {
                        navigateWithAnimation(
                            requireNotNull(fragmentIdToLoad, { "fragmentIdToLoad is null" })
                        )
                    }
                }
            }
        )

        navController.addOnDestinationChangedListener { _, destination, bundle ->
            fragmentIdToLoad = destination.id
            when (fragmentIdToLoad) {
                R.id.DisplayLabel ->
                    bundle?.getString(EXTRA_DISPLAYED_LABEL)?.let {
                        baseModel.currentLabel = it
                        binding.NavigationView.menu.children
                            .find { menuItem -> menuItem.title == it }
                            ?.let { menuItem -> menuItem.isChecked = true }
                    }
                R.id.Unlabeled -> {
                    baseModel.currentLabel = CURRENT_LABEL_NONE
                    binding.NavigationView.setCheckedItem(destination.id)
                }
                else -> {
                    baseModel.currentLabel = CURRENT_LABEL_EMPTY
                    binding.NavigationView.setCheckedItem(destination.id)
                }
            }
            when (destination.id) {
                R.id.Notes,
                R.id.DisplayLabel,
                R.id.Unlabeled -> {
                    binding.TakeNote.show()
                    binding.MakeList.show()
                }

                else -> {
                    binding.TakeNote.hide()
                    binding.MakeList.hide()
                }
            }
            isStartViewFragment = isStartViewFragment(destination.id, bundle)
        }
    }

    private fun isStartViewFragment(id: Int, bundle: Bundle?): Boolean {
        val (startViewId, startViewBundle) = getStartViewNavigation()
        return startViewId == id &&
            startViewBundle.getString(EXTRA_DISPLAYED_LABEL) ==
                bundle?.getString(EXTRA_DISPLAYED_LABEL)
    }

    private fun navigateWithAnimation(id: Int) {
        val options = navOptions {
            launchSingleTop = true
            anim {
                exit = androidx.navigation.ui.R.anim.nav_default_exit_anim
                enter = androidx.navigation.ui.R.anim.nav_default_enter_anim
                popExit = androidx.navigation.ui.R.anim.nav_default_pop_exit_anim
                popEnter = androidx.navigation.ui.R.anim.nav_default_pop_enter_anim
            }
            popUpTo(navController.graph.startDestination) { inclusive = false }
        }
        navController.navigate(id, null, options)
    }

    private fun setupActivityResultLaunchers() {
        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        baseModel.exportSelectedNoteToFile(uri, binding.root)
                    }
                }
            }
        exportNotesActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        baseModel.exportSelectedNotesToFolder(uri, binding.root)
                    }
                }
            }
    }

    private inner class ModelFolderObserver(
        private val menu: Menu,
        private val model: BaseNoteModel,
    ) : Observer<Folder> {
        override fun onChanged(value: Folder) {
            menu.clear()
            model.actionMode.count.removeObservers(this@MainActivity)

            menu.add(
                R.string.select_all,
                R.drawable.select_all,
                showAsAction = MenuItem.SHOW_AS_ACTION_ALWAYS,
            ) {
                getCurrentFragmentNotes?.invoke()?.let { model.actionMode.add(it) }
            }
            when (value) {
                Folder.NOTES -> {
                    val pinned = menu.addPinned(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addLabels(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addDelete(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(R.string.archive, R.drawable.archive) { moveNotes(Folder.ARCHIVED) }
                    menu.addChangeColor()
                    val share = menu.addShare()
                    menu.addExportMenu()
                    model.actionMode.count.observeCountAndPinned(this@MainActivity, share, pinned)
                }

                Folder.ARCHIVED -> {
                    menu.add(
                        R.string.unarchive,
                        R.drawable.unarchive,
                        MenuItem.SHOW_AS_ACTION_ALWAYS,
                    ) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.addDelete(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.addExportMenu(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    val pinned = menu.addPinned()
                    menu.addLabels()
                    menu.addChangeColor()
                    val share = menu.addShare()
                    model.actionMode.count.observeCountAndPinned(this@MainActivity, share, pinned)
                }

                Folder.DELETED -> {
                    menu.add(R.string.restore, R.drawable.restore, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                        moveNotes(Folder.NOTES)
                    }
                    menu.add(
                        R.string.delete_forever,
                        R.drawable.delete,
                        MenuItem.SHOW_AS_ACTION_ALWAYS,
                    ) {
                        deleteForever()
                    }
                    menu.addExportMenu()
                    menu.addChangeColor()
                    val share = menu.add(R.string.share, R.drawable.share) { share() }
                    model.actionMode.count.observeCount(this@MainActivity, share)
                }
            }
        }

        private fun Menu.addPinned(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.pin, R.drawable.pin, showAsAction) {}
        }

        private fun Menu.addLabels(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.labels, R.drawable.label, showAsAction) { label() }
        }

        private fun Menu.addChangeColor(
            showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM
        ): MenuItem {
            return add(R.string.change_color, R.drawable.change_color, showAsAction) {
                lifecycleScope.launch {
                    val colors =
                        withContext(Dispatchers.IO) {
                            NotallyDatabase.getDatabase(
                                    this@MainActivity,
                                    observePreferences = false,
                                )
                                .value
                                .getBaseNoteDao()
                                .getAllColors()
                        }
                    // Show color as selected only if all selected notes have the same color
                    val currentColor =
                        model.actionMode.selectedNotes.values
                            .map { it.color }
                            .distinct()
                            .takeIf { it.size == 1 }
                            ?.firstOrNull()
                    showColorSelectDialog(
                        colors,
                        currentColor,
                        null,
                        { selectedColor, oldColor ->
                            if (oldColor != null) {
                                model.changeColor(oldColor, selectedColor)
                            }
                            model.colorBaseNote(selectedColor)
                        },
                    ) { colorToDelete, newColor ->
                        model.changeColor(colorToDelete, newColor)
                    }
                }
            }
        }

        private fun Menu.addDelete(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.delete, R.drawable.delete, showAsAction) {
                moveNotes(Folder.DELETED)
            }
        }

        private fun Menu.addShare(showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM): MenuItem {
            return add(R.string.share, R.drawable.share, showAsAction) { share() }
        }

        private fun Menu.addExportMenu(
            showAsAction: Int = MenuItem.SHOW_AS_ACTION_IF_ROOM
        ): MenuItem {
            return addSubMenu(R.string.export)
                .apply {
                    setIcon(R.drawable.export)
                    item.setShowAsAction(showAsAction)
                    ExportMimeType.entries.forEach {
                        add(it.name).onClick { exportSelectedNotes(it) }
                    }
                }
                .item
        }

        fun MenuItem.onClick(function: () -> Unit) {
            setOnMenuItemClickListener {
                function()
                return@setOnMenuItemClickListener false
            }
        }

        private fun NotNullLiveData<Int>.observeCount(
            lifecycleOwner: LifecycleOwner,
            share: MenuItem,
            onCountChange: ((Int) -> Unit)? = null,
        ) {
            observe(lifecycleOwner) { count ->
                binding.ActionMode.title = count.toString()
                onCountChange?.invoke(count)
                share.setVisible(count == 1)
            }
        }

        private fun NotNullLiveData<Int>.observeCountAndPinned(
            lifecycleOwner: LifecycleOwner,
            share: MenuItem,
            pinned: MenuItem,
        ) {
            observeCount(lifecycleOwner, share) {
                val baseNotes = model.actionMode.selectedNotes.values
                if (baseNotes.any { !it.pinned }) {
                    pinned.setTitle(R.string.pin).setIcon(R.drawable.pin).onClick {
                        model.pinBaseNotes(true)
                    }
                } else {
                    pinned.setTitle(R.string.unpin).setIcon(R.drawable.unpin).onClick {
                        model.pinBaseNotes(false)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Only show search icon if preference is not enabled and not in Reminders or Settings
        // fragments
        val currentDestinationId = navController.currentDestination?.id
        if (
            !preferences.alwaysShowSearchBar.value &&
                !ACTIVITES_WITHOUT_SEARCH.contains(currentDestinationId)
        ) {

            // If in Search fragment, show X icon instead of search icon
            val isInSearchFragment = currentDestinationId == R.id.Search
            val iconRes = if (isInSearchFragment) R.drawable.close else R.drawable.search
            val titleRes = if (isInSearchFragment) R.string.cancel else R.string.search

            menu
                .add(Menu.NONE, ACTION_SEARCH, Menu.NONE, titleRes)
                .setIcon(iconRes)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            ACTION_SEARCH -> {
                val isInSearchFragment = navController.currentDestination?.id == R.id.Search

                if (isInSearchFragment) {
                    // If in Search fragment, navigate back to cancel search
                    baseModel.keyword = ""
                    navController.popBackStack()
                } else {
                    // Navigate to search fragment
                    val currentFragment =
                        supportFragmentManager
                            .findFragmentById(R.id.NavHostFragment)
                            ?.childFragmentManager
                            ?.fragments
                            ?.firstOrNull()

                    if (currentFragment is NotallyFragment) {
                        navController.navigate(
                            R.id.Search,
                            Bundle().apply {
                                putSerializable(
                                    SearchFragment.EXTRA_INITIAL_FOLDER,
                                    baseModel.folder.value,
                                )
                                putSerializable(
                                    SearchFragment.EXTRA_INITIAL_LABEL,
                                    baseModel.currentLabel,
                                )
                            },
                        )
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_FRAGMENT_TO_OPEN = "notallyx.intent.extra.FRAGMENT_TO_OPEN"
        const val EXTRA_SKIP_START_VIEW_ON_BACK = "notallyx.intent.extra.SKIP_START_VIEW_ON_BACK"
        private const val ACTION_SEARCH = 1001
        val ACTIVITES_WITHOUT_SEARCH = setOf(R.id.Settings, R.id.Reminders, R.id.Labels)
    }
}
