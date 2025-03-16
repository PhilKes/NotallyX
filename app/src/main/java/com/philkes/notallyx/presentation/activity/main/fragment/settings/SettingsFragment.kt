package com.philkes.notallyx.presentation.activity.main.fragment.settings

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT_TREE
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.FOLDER_OR_FILE_MIMETYPE
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.data.imports.txt.APPLICATION_TEXT_MIME_TYPES
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.databinding.DialogTextInputBinding
import com.philkes.notallyx.databinding.FragmentSettingsBinding
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.setupImportProgressDialog
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.presentation.showDialog
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.TextWithIconAdapter
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.LongPreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.PeriodicBackup
import com.philkes.notallyx.presentation.viewmodel.preference.PeriodicBackup.Companion.BACKUP_MAX_MIN
import com.philkes.notallyx.presentation.viewmodel.preference.PeriodicBackup.Companion.BACKUP_PERIOD_DAYS_MIN
import com.philkes.notallyx.presentation.viewmodel.preference.PeriodicBackupsPreference
import com.philkes.notallyx.utils.MIME_TYPE_JSON
import com.philkes.notallyx.utils.MIME_TYPE_ZIP
import com.philkes.notallyx.utils.backup.exportPreferences
import com.philkes.notallyx.utils.catchNoBrowserInstalled
import com.philkes.notallyx.utils.getLastExceptionLog
import com.philkes.notallyx.utils.getLogFile
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.reportBug
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt
import com.philkes.notallyx.utils.wrapWithChooser
import java.util.Date

class SettingsFragment : Fragment() {

    private val model: BaseNoteModel by activityViewModels()

    private lateinit var importBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var importOtherActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var chooseBackupFolderActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var setupLockActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var disableLockActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportSettingsActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var importSettingsActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectedImportSource: ImportSource

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentSettingsBinding.inflate(inflater)
        model.preferences.apply {
            setupAppearance(binding)
            setupContentDensity(binding)
            setupBackup(binding)
            setupAutoBackups(binding)
            setupSecurity(binding)
            setupSettings(binding)
        }
        setupAbout(binding)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActivityResultLaunchers()
        savedInstanceState?.getBoolean(EXTRA_SHOW_IMPORT_BACKUPS_FOLDER, false)?.let {
            if (it) {
                model.refreshBackupsFolder(
                    requireContext(),
                    askForUriPermissions = ::askForUriPermissions,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (model.showRefreshBackupsFolderAfterThemeChange) {
            outState.putBoolean(EXTRA_SHOW_IMPORT_BACKUPS_FOLDER, true)
        }
    }

    private fun setupActivityResultLaunchers() {
        importBackupActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { importBackup(it) }
                }
            }
        importOtherActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.importFromOtherApp(uri, selectedImportSource)
                    }
                }
            }
        exportBackupActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> model.exportBackup(uri) }
                }
            }
        chooseBackupFolderActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.setupBackupsFolder(uri)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            activity?.let {
                                val permission = Manifest.permission.POST_NOTIFICATIONS
                                if (
                                    it.checkSelfPermission(permission) !=
                                        PackageManager.PERMISSION_GRANTED
                                ) {
                                    MaterialAlertDialogBuilder(it)
                                        .setMessage(
                                            R.string.please_grant_notally_notification_auto_backup
                                        )
                                        .setNegativeButton(R.string.skip, null)
                                        .setPositiveButton(R.string.continue_) { _, _ ->
                                            it.requestPermissions(arrayOf(permission), 0)
                                        }
                                        .show()
                                }
                            }
                        }
                    }
                }
            }
        setupLockActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                showEnableBiometricLock()
            }
        disableLockActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                showDisableBiometricLock()
            }
        exportSettingsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        if (requireContext().exportPreferences(model.preferences, uri)) {
                            showToast(R.string.export_settings_success)
                        } else {
                            showToast(R.string.export_settings_failure)
                        }
                    }
                }
            }
        importSettingsActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri ->
                        model.importPreferences(
                            requireContext(),
                            uri,
                            ::askForUriPermissions,
                            { showToast(R.string.import_settings_success) },
                        ) {
                            showToast(R.string.import_settings_failure)
                        }
                    }
                }
            }
    }

    private fun importBackup(uri: Uri) {
        when (requireContext().contentResolver.getType(uri)) {
            "text/xml" -> {
                model.importXmlBackup(uri)
            }

            MIME_TYPE_ZIP -> {
                val layout = DialogTextInputBinding.inflate(layoutInflater, null, false)
                val password = model.preferences.backupPassword.value
                layout.InputText.apply {
                    if (password != PASSWORD_EMPTY) {
                        setText(password)
                    }
                    transformationMethod = PasswordTransformationMethod.getInstance()
                }
                layout.InputTextLayout.endIconMode = END_ICON_PASSWORD_TOGGLE
                layout.Message.apply {
                    setText(R.string.import_backup_password_hint)
                    visibility = View.VISIBLE
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.backup_password)
                    .setView(layout.root)
                    .setPositiveButton(R.string.import_backup) { dialog, _ ->
                        dialog.cancel()
                        val usedPassword = layout.InputText.text.toString()
                        model.importZipBackup(uri, usedPassword)
                    }
                    .setCancelButton()
                    .show()
            }
        }
    }

    private fun NotallyXPreferences.setupAppearance(binding: FragmentSettingsBinding) {
        notesView.observe(viewLifecycleOwner) { value ->
            binding.View.setup(notesView, value, requireContext()) { newValue ->
                model.savePreference(notesView, newValue)
            }
        }

        theme.observe(viewLifecycleOwner) { value ->
            binding.Theme.setup(theme, value, requireContext()) { newValue ->
                model.savePreference(theme, newValue)
            }
        }

        dateFormat.merge(applyDateFormatInNoteView).observe(viewLifecycleOwner) {
            (dateFormatValue, applyDateFormatInEditNoteValue) ->
            binding.DateFormat.setup(
                dateFormat,
                dateFormatValue,
                applyDateFormatInEditNoteValue,
                requireContext(),
                layoutInflater,
            ) { newDateFormatValue, newApplyDateFormatInEditNote ->
                model.savePreference(dateFormat, newDateFormatValue)
                model.savePreference(applyDateFormatInNoteView, newApplyDateFormatInEditNote)
            }
        }

        textSize.observe(viewLifecycleOwner) { value ->
            binding.TextSize.setup(textSize, value, requireContext()) { newValue ->
                model.savePreference(textSize, newValue)
            }
        }

        notesSorting.observe(viewLifecycleOwner) { notesSort ->
            binding.NotesSortOrder.setup(
                notesSorting,
                notesSort,
                requireContext(),
                layoutInflater,
                model,
            )
        }

        listItemSorting.observe(viewLifecycleOwner) { value ->
            binding.CheckedListItemSorting.setup(listItemSorting, value, requireContext()) {
                newValue ->
                model.savePreference(listItemSorting, newValue)
            }
        }

        binding.MaxLabels.setup(maxLabels, requireContext()) { newValue ->
            model.savePreference(maxLabels, newValue)
        }

        startView.merge(model.labels).observe(viewLifecycleOwner) { (startViewValue, labelsValue) ->
            binding.StartView.setupStartView(
                startView,
                startViewValue,
                labelsValue,
                requireContext(),
                layoutInflater,
            ) { newValue ->
                model.savePreference(startView, newValue)
            }
        }
    }

    private fun NotallyXPreferences.setupContentDensity(binding: FragmentSettingsBinding) {
        binding.apply {
            MaxTitle.setup(maxTitle, requireContext()) { newValue ->
                model.savePreference(maxTitle, newValue)
            }
            MaxItems.setup(maxItems, requireContext()) { newValue ->
                model.savePreference(maxItems, newValue)
            }

            MaxLines.setup(maxLines, requireContext()) { newValue ->
                model.savePreference(maxLines, newValue)
            }
            MaxLabels.setup(maxLabels, requireContext()) { newValue ->
                model.savePreference(maxLabels, newValue)
            }
            labelsHiddenInOverview.observe(viewLifecycleOwner) { value ->
                binding.LabelsHiddenInOverview.setup(
                    labelsHiddenInOverview,
                    value,
                    requireContext(),
                    layoutInflater,
                    R.string.labels_hidden_in_overview,
                ) { enabled ->
                    model.savePreference(labelsHiddenInOverview, enabled)
                }
            }
        }
    }

    private fun NotallyXPreferences.setupBackup(binding: FragmentSettingsBinding) {
        binding.apply {
            ImportBackup.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(MIME_TYPE_ZIP, "text/xml"))
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        .wrapWithChooser(requireContext())
                importBackupActivityResultLauncher.launch(intent)
            }
            ImportOther.setOnClickListener { importFromOtherApp() }
            ExportBackup.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .apply {
                            type = MIME_TYPE_ZIP
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "NotallyX Backup")
                        }
                        .wrapWithChooser(requireContext())
                exportBackupActivityResultLauncher.launch(intent)
            }
        }
        model.exportProgress.setupProgressDialog(this@SettingsFragment, R.string.exporting_backup)
        model.importProgress.setupImportProgressDialog(
            this@SettingsFragment,
            R.string.importing_backup,
        )
    }

    private fun NotallyXPreferences.setupAutoBackups(binding: FragmentSettingsBinding) {
        backupsFolder.observe(viewLifecycleOwner) { value ->
            binding.BackupsFolder.setupBackupsFolder(
                value,
                requireContext(),
                ::displayChooseBackupFolderDialog,
            ) {
                model.disableBackups()
            }
        }
        backupOnSave.merge(backupsFolder).observe(viewLifecycleOwner) { (onSave, backupFolder) ->
            binding.BackupOnSave.setup(
                backupOnSave,
                onSave,
                requireContext(),
                layoutInflater,
                messageResId = R.string.auto_backup_on_save,
                enabled = backupFolder != EMPTY_PATH,
                disabledTextResId = R.string.auto_backups_folder_set,
            ) { enabled ->
                model.savePreference(backupOnSave, enabled)
            }
        }
        periodicBackups.merge(backupsFolder).observe(viewLifecycleOwner) {
            (periodicBackup, backupFolder) ->
            setupPeriodicBackup(
                binding,
                periodicBackup,
                backupFolder,
                periodicBackups,
                periodicBackupLastExecution,
            )
        }
    }

    private fun importFromOtherApp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_other_app)
            .setAdapter(
                TextWithIconAdapter(
                    requireContext(),
                    ImportSource.entries.toMutableList(),
                    { item -> getString(item.displayNameResId) },
                    ImportSource::iconResId,
                )
            ) { _, which ->
                selectedImportSource = ImportSource.entries[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(selectedImportSource.helpTextResId)
                    .setPositiveButton(R.string.import_action) { dialog, _ ->
                        dialog.cancel()
                        when (selectedImportSource.mimeType) {
                            FOLDER_OR_FILE_MIMETYPE ->
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.plain_text_files)
                                    .setItems(
                                        arrayOf(
                                            getString(R.string.folder),
                                            getString(R.string.single_file),
                                        )
                                    ) { _, which ->
                                        when (which) {
                                            0 ->
                                                importOtherActivityResultLauncher.launch(
                                                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                        .apply {
                                                            addCategory(Intent.CATEGORY_DEFAULT)
                                                        }
                                                        .wrapWithChooser(requireContext())
                                                )
                                            1 ->
                                                importOtherActivityResultLauncher.launch(
                                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                        .apply {
                                                            type = "text/*"
                                                            addCategory(Intent.CATEGORY_OPENABLE)
                                                            putExtra(
                                                                Intent.EXTRA_MIME_TYPES,
                                                                arrayOf("text/*") +
                                                                    APPLICATION_TEXT_MIME_TYPES,
                                                            )
                                                        }
                                                        .wrapWithChooser(requireContext())
                                                )
                                        }
                                    }
                                    .setCancelButton()
                                    .show()
                            else ->
                                importOtherActivityResultLauncher.launch(
                                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                                        .apply {
                                            type = "application/*"
                                            putExtra(
                                                Intent.EXTRA_MIME_TYPES,
                                                arrayOf(selectedImportSource.mimeType),
                                            )
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                        }
                                        .wrapWithChooser(requireContext())
                                )
                        }
                    }
                    .also {
                        selectedImportSource.documentationUrl?.let<String, Unit> { docUrl ->
                            it.setNegativeButton(R.string.help) { _, _ ->
                                val intent =
                                    Intent(Intent.ACTION_VIEW)
                                        .apply { data = Uri.parse(docUrl) }
                                        .wrapWithChooser(requireContext())
                                startActivity(intent)
                            }
                        }
                    }
                    .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .showAndFocus(allowFullSize = true)
            }
            .setCancelButton()
            .show()
    }

    private fun setupPeriodicBackup(
        binding: FragmentSettingsBinding,
        value: PeriodicBackup,
        backupFolder: String,
        preference: PeriodicBackupsPreference,
        lastExecutionPreference: LongPreference,
    ) {
        val periodicBackupsEnabled = value.periodInDays > 0 && backupFolder != EMPTY_PATH
        binding.PeriodicBackups.setupPeriodicBackup(
            periodicBackupsEnabled,
            requireContext(),
            layoutInflater,
            enabled = backupFolder != EMPTY_PATH,
        ) { enabled ->
            if (enabled) {
                val periodInDays =
                    preference.value.periodInDays.let {
                        if (it >= BACKUP_PERIOD_DAYS_MIN) it else BACKUP_PERIOD_DAYS_MIN
                    }
                val maxBackups =
                    preference.value.maxBackups.let {
                        if (it >= BACKUP_MAX_MIN) it else BACKUP_MAX_MIN
                    }
                model.savePreference(
                    preference,
                    preference.value.copy(periodInDays = periodInDays, maxBackups = maxBackups),
                )
            } else {
                model.savePreference(preference, preference.value.copy(periodInDays = 0))
            }
        }
        lastExecutionPreference.observe(viewLifecycleOwner) { time ->
            binding.PeriodicBackupLastExecution.apply {
                if (time != -1L) {
                    isVisible = true
                    text =
                        "${requireContext().getString(R.string.auto_backup_last)}: ${Date(time).toText()}"
                } else isVisible = false
            }
        }
        binding.PeriodicBackupsPeriodInDays.setup(
            value.periodInDays,
            R.string.backup_period_days,
            PeriodicBackup.BACKUP_PERIOD_DAYS_MIN,
            PeriodicBackup.BACKUP_PERIOD_DAYS_MAX,
            requireContext(),
            enabled = periodicBackupsEnabled,
        ) { newValue ->
            model.savePreference(preference, preference.value.copy(periodInDays = newValue))
        }
        binding.PeriodicBackupsMax.setup(
            value.maxBackups,
            R.string.max_backups,
            PeriodicBackup.BACKUP_MAX_MIN,
            PeriodicBackup.BACKUP_MAX_MAX,
            requireContext(),
            enabled = periodicBackupsEnabled,
        ) { newValue: Int ->
            model.savePreference(preference, preference.value.copy(maxBackups = newValue))
        }
    }

    private fun NotallyXPreferences.setupSecurity(binding: FragmentSettingsBinding) {
        biometricLock.observe(viewLifecycleOwner) { value ->
            binding.BiometricLock.setup(
                biometricLock,
                value,
                requireContext(),
                model,
                ::showEnableBiometricLock,
                ::showDisableBiometricLock,
                ::showBiometricsNotSetupDialog,
            )
        }

        backupPassword.observe(viewLifecycleOwner) { value ->
            binding.BackupPassword.setupBackupPassword(
                backupPassword,
                value,
                requireContext(),
                layoutInflater,
            ) { newValue ->
                model.savePreference(backupPassword, newValue)
            }
        }
    }

    private fun NotallyXPreferences.setupSettings(binding: FragmentSettingsBinding) {
        binding.apply {
            ImportSettings.setOnClickListener {
                showDialog(R.string.import_settings_message, R.string.import_action) { _, _ ->
                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .apply {
                                type = MIME_TYPE_JSON
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "NotallyX_Settings.json")
                            }
                            .wrapWithChooser(requireContext())
                    importSettingsActivityResultLauncher.launch(intent)
                }
            }
            ExportSettings.setOnClickListener {
                showDialog(R.string.export_settings_message, R.string.export) { _, _ ->
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .apply {
                                type = MIME_TYPE_JSON
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "NotallyX_Settings.json")
                            }
                            .wrapWithChooser(requireContext())
                    exportSettingsActivityResultLauncher.launch(intent)
                }
            }
            ResetSettings.setOnClickListener {
                showDialog(R.string.reset_settings_message, R.string.reset_settings) { _, _ ->
                    model.resetPreferences { _ -> showToast(R.string.reset_settings_success) }
                }
            }
            dataInPublicFolder.observe(viewLifecycleOwner) { value ->
                binding.DataInPublicFolder.setup(
                    dataInPublicFolder,
                    value,
                    requireContext(),
                    layoutInflater,
                    R.string.data_in_public_message,
                ) { enabled ->
                    if (enabled) {
                        model.enableDataInPublic()
                    } else {
                        model.disableDataInPublic()
                    }
                }
            }
            binding.AutoSaveAfterIdle.setupAutoSaveIdleTime(
                autoSaveAfterIdleTime,
                requireContext(),
            ) { newValue ->
                model.savePreference(autoSaveAfterIdleTime, newValue)
            }
            ClearData.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.clear_data_message)
                    .setPositiveButton(R.string.delete_all) { _, _ -> model.deleteAll() }
                    .setCancelButton()
                    .show()
            }
        }
        model.deletionProgress.setupProgressDialog(this@SettingsFragment, R.string.deleting_files)
    }

    private fun setupAbout(binding: FragmentSettingsBinding) {
        binding.apply {
            SendFeedback.setOnClickListener {
                val options =
                    arrayOf(
                        getString(R.string.report_bug),
                        getString(R.string.make_feature_request),
                        getString(R.string.send_feedback),
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.send_feedback)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                val app = requireContext().applicationContext as Application
                                val logs = app.getLastExceptionLog()
                                reportBug(logs)
                            }

                            1 ->
                                requireContext().catchNoBrowserInstalled {
                                    startActivity(
                                        Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    "https://github.com/PhilKes/NotallyX/issues/new?labels=enhancement&template=feature_request.md"
                                                ),
                                            )
                                            .wrapWithChooser(requireContext())
                                    )
                                }
                            2 -> {
                                val intent =
                                    Intent(Intent.ACTION_SEND)
                                        .apply {
                                            selector =
                                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                                            putExtra(
                                                Intent.EXTRA_EMAIL,
                                                arrayOf("notallyx@yahoo.com"),
                                            )
                                            putExtra(Intent.EXTRA_SUBJECT, "NotallyX [Feedback]")
                                            val app =
                                                requireContext().applicationContext as Application
                                            val log = app.getLogFile()
                                            if (log.exists()) {
                                                val uri = app.getUriForFile(log)
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                            }
                                        }
                                        .wrapWithChooser(requireContext())
                                try {
                                    startActivity(intent)
                                } catch (exception: ActivityNotFoundException) {
                                    showToast(R.string.install_an_email)
                                }
                            }
                        }
                    }
                    .setCancelButton()
                    .show()
            }
            Rate.setOnClickListener {
                openLink("https://play.google.com/store/apps/details?id=com.philkes.notallyx")
            }
            SourceCode.setOnClickListener { openLink("https://github.com/PhilKes/NotallyX") }
            Libraries.setOnClickListener {
                val libraries =
                    arrayOf(
                        "Glide",
                        "Pretty Time",
                        "SwipeDrawer",
                        "Work Manager",
                        "Subsampling Scale ImageView",
                        "Material Components for Android",
                        "SQLCipher",
                        "Zip4J",
                        "AndroidFastScroll",
                        "ColorPickerView",
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.libraries)
                    .setItems(libraries) { _, which ->
                        when (which) {
                            0 -> openLink("https://github.com/bumptech/glide")
                            1 -> openLink("https://github.com/ocpsoft/prettytime")
                            2 -> openLink("https://leaqi.github.io/SwipeDrawer_en")
                            3 ->
                                openLink(
                                    "https://developer.android.com/jetpack/androidx/releases/work"
                                )
                            4 ->
                                openLink(
                                    "https://github.com/davemorrissey/subsampling-scale-image-view"
                                )
                            5 ->
                                openLink(
                                    "https://github.com/material-components/material-components-android"
                                )
                            6 -> openLink("https://github.com/sqlcipher/sqlcipher")
                            7 -> openLink("https://github.com/srikanth-lingala/zip4j")
                            8 -> openLink("https://github.com/zhanghai/AndroidFastScroll")
                            9 -> openLink("https://github.com/skydoves/ColorPickerView")
                        }
                    }
                    .setCancelButton()
                    .show()
            }
            Donate.setOnClickListener { openLink("https://ko-fi.com/philkes") }

            try {
                val pInfo =
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                val version = pInfo.versionName
                VersionText.text = "v$version"
            } catch (_: PackageManager.NameNotFoundException) {}
        }
    }

    private fun displayChooseBackupFolderDialog() {
        showDialog(R.string.auto_backups_folder_hint, R.string.choose_folder) { _, _ ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).wrapWithChooser(requireContext())
            chooseBackupFolderActivityResultLauncher.launch(intent)
        }
    }

    private fun showEnableBiometricLock() {
        showBiometricOrPinPrompt(
            false,
            setupLockActivityResultLauncher,
            R.string.enable_lock_title,
            R.string.enable_lock_description,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    model.enableBiometricLock(cipher)
                }
                val app = (activity?.application as NotallyXApplication)
                app.locked.value = false
                showToast(R.string.biometrics_setup_success)
            },
        ) {
            showBiometricsNotSetupDialog()
        }
    }

    private fun showDisableBiometricLock() {
        showBiometricOrPinPrompt(
            true,
            disableLockActivityResultLauncher,
            R.string.disable_lock_title,
            R.string.disable_lock_description,
            model.preferences.iv.value!!,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    model.disableBiometricLock(cipher)
                }
                showToast(R.string.biometrics_disable_success)
            },
        ) {}
    }

    private fun showBiometricsNotSetupDialog() {
        showDialog(R.string.biometrics_not_setup, R.string.tap_to_set_up) { _, _ ->
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Intent(Settings.ACTION_FINGERPRINT_ENROLL)
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }
            setupLockActivityResultLauncher.launch(intent)
        }
    }

    private fun openLink(link: String) {
        val uri = Uri.parse(link)
        val intent = Intent(Intent.ACTION_VIEW, uri).wrapWithChooser(requireContext())
        startActivity(intent)
    }

    private fun askForUriPermissions(uri: Uri) {
        chooseBackupFolderActivityResultLauncher.launch(
            Intent(ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }
        )
    }

    companion object {
        const val EXTRA_SHOW_IMPORT_BACKUPS_FOLDER =
            "notallyx.intent.extra.SHOW_IMPORT_BACKUPS_FOLDER"
    }
}
