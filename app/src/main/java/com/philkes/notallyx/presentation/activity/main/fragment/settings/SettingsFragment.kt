package com.philkes.notallyx.presentation.activity.main.fragment.settings

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.FOLDER_OR_FILE_MIMETYPE
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.data.imports.txt.APPLICATION_TEXT_MIME_TYPES
import com.philkes.notallyx.databinding.DialogTextInputBinding
import com.philkes.notallyx.databinding.FragmentSettingsBinding
import com.philkes.notallyx.presentation.addCancelButton
import com.philkes.notallyx.presentation.setupImportProgressDialog
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.showDialog
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.TextWithIconAdapter
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.AutoBackup
import com.philkes.notallyx.presentation.viewmodel.preference.AutoBackupPreference
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.LongPreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences
import com.philkes.notallyx.utils.backup.exportPreferences
import com.philkes.notallyx.utils.catchNoBrowserInstalled
import com.philkes.notallyx.utils.getLastExceptionLog
import com.philkes.notallyx.utils.getLogFile
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.reportBug
import com.philkes.notallyx.utils.security.disableBiometricLock
import com.philkes.notallyx.utils.security.encryptDatabase
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt
import com.philkes.notallyx.utils.wrapWithChooser

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
            setupAutoBackup(binding)
            setupSecurity(binding)
        }
        setupSettings(binding)
        setupAbout(binding)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActivityResultLaunchers()
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
                        model.setAutoBackupPath(uri)
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
                        if (model.importPreferences(requireContext(), uri)) {
                            showToast(R.string.import_settings_success)
                        } else {
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

            "application/zip" -> {
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
                    .addCancelButton()
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
        // TODO: Hide for now until checked auto-sort is working reliably
        //            listItemSorting.observe(viewLifecycleOwner) { value ->
        //                binding.CheckedListItemSorting.setup(ListItemSorting, value)
        //            }

        binding.MaxLabels.setup(maxLabels, requireContext()) { newValue ->
            model.savePreference(maxLabels, newValue)
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

    private fun NotallyXPreferences.setupAutoBackup(binding: FragmentSettingsBinding) {
        autoBackup.observe(viewLifecycleOwner) { value ->
            setupAutoBackup(binding, value, autoBackup, autoBackupLastExecutionTime)
        }

        binding.apply {
            ImportBackup.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .apply {
                            type = "*/*"
                            putExtra(
                                Intent.EXTRA_MIME_TYPES,
                                arrayOf("application/zip", "text/xml"),
                            )
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
                            type = "application/zip"
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

        dataOnExternalStorage.observe(viewLifecycleOwner) { value ->
            binding.ExternalDataFolder.setup(
                dataOnExternalStorage,
                value,
                requireContext(),
                layoutInflater,
                R.string.external_data_message,
            ) { enabled ->
                if (enabled) {
                    model.enableExternalData()
                } else {
                    model.disableExternalData()
                }
            }
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
                                    .addCancelButton()
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
                    .show()
            }
            .addCancelButton()
            .show()
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

    private fun setupSettings(binding: FragmentSettingsBinding) {
        binding.apply {
            ImportSettings.setOnClickListener {
                showDialog(R.string.import_settings_message, R.string.import_action) { _, _ ->
                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .apply {
                                type = "application/json"
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
                                type = "application/json"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "NotallyX_Settings.json")
                            }
                            .wrapWithChooser(requireContext())
                    exportSettingsActivityResultLauncher.launch(intent)
                }
            }
            ResetSettings.setOnClickListener {
                showDialog(R.string.reset_settings_message, R.string.reset_settings) { _, _ ->
                    model.resetPreferences()
                    showToast(R.string.reset_settings_success)
                }
            }
            ClearData.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.clear_data_message)
                    .setPositiveButton(R.string.delete_all) { _, _ -> model.deleteAll() }
                    .addCancelButton()
                    .show()
            }
        }
        model.deletionProgress.setupProgressDialog(this, R.string.deleting_files)
    }

    private fun setupAbout(binding: FragmentSettingsBinding) {
        binding.apply {
            SendFeedback.setOnClickListener {
                val intent =
                    Intent(Intent.ACTION_SEND)
                        .apply {
                            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("notallyx@yahoo.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "NotallyX [Feedback]")
                            val app = requireContext().applicationContext as Application
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
            CreateIssue.setOnClickListener {
                val options =
                    arrayOf(
                        getString(R.string.report_bug),
                        getString(R.string.make_feature_request),
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.create_github_issue)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> {
                                val app = requireContext().applicationContext as Application
                                val logs = app.getLastExceptionLog()
                                reportBug(logs)
                            }

                            else ->
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
                        }
                    }
                    .addCancelButton()
                    .show()
            }
            SourceCode.setOnClickListener { openLink("https://github.com/PhilKes/NotallyX") }
            Libraries.setOnClickListener {
                val libraries =
                    arrayOf(
                        "Glide",
                        "Pretty Time",
                        "Swipe Layout",
                        "Work Manager",
                        "Subsampling Scale ImageView",
                        "Material Components for Android",
                        "SQLCipher",
                        "Zip4J",
                        "AndroidFastScroll",
                    )
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.libraries)
                    .setItems(libraries) { _, which ->
                        when (which) {
                            0 -> openLink("https://github.com/bumptech/glide")
                            1 -> openLink("https://github.com/ocpsoft/prettytime")
                            2 -> openLink("https://github.com/zerobranch/SwipeLayout")
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
                        }
                    }
                    .addCancelButton()
                    .show()
            }

            try {
                val pInfo =
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                val version = pInfo.versionName
                VersionText.text = "v$version"
            } catch (_: PackageManager.NameNotFoundException) {}
        }
    }

    private fun setupAutoBackup(
        binding: FragmentSettingsBinding,
        value: AutoBackup,
        preference: AutoBackupPreference,
        lastExecutionPreference: LongPreference,
    ) {
        binding.AutoBackupMax.setup(
            value.maxBackups,
            R.string.max_backups,
            AutoBackup.BACKUP_MAX_MIN,
            AutoBackup.BACKUP_MAX_MAX,
            requireContext(),
        ) { newValue: Int ->
            model.savePreference(preference, preference.value.copy(maxBackups = newValue))
        }
        binding.AutoBackup.setupAutoBackup(
            value.path,
            requireContext(),
            viewLifecycleOwner,
            lastExecutionPreference,
            ::displayChooseBackupFolderDialog,
        ) {
            model.disableAutoBackup()
        }
        binding.AutoBackupPeriodDays.setup(
            value.periodInDays,
            R.string.backup_period_days,
            AutoBackup.BACKUP_PERIOD_DAYS_MIN,
            AutoBackup.BACKUP_PERIOD_DAYS_MAX,
            requireContext(),
        ) { newValue ->
            model.savePreference(preference, preference.value.copy(periodInDays = newValue))
        }
    }

    private fun displayChooseBackupFolderDialog() {
        showDialog(R.string.notes_will_be, R.string.choose_folder) { _, _ ->
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
                    model.savePreference(model.preferences.iv, cipher.iv)
                    val passphrase = model.preferences.databaseEncryptionKey.init(cipher)
                    encryptDatabase(requireContext(), passphrase)
                    model.savePreference(
                        model.preferences.fallbackDatabaseEncryptionKey,
                        passphrase,
                    )
                    model.savePreference(model.preferences.biometricLock, BiometricLock.ENABLED)
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
                    requireContext().disableBiometricLock(model, cipher)
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
}
