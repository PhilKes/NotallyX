package com.philkes.notallyx.presentation.activity.main.fragment

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.data.imports.ImportSource
import com.philkes.notallyx.databinding.ChoiceItemBinding
import com.philkes.notallyx.databinding.FragmentSettingsBinding
import com.philkes.notallyx.databinding.NotesSortDialogBinding
import com.philkes.notallyx.databinding.PreferenceBinding
import com.philkes.notallyx.databinding.PreferenceBooleanDialogBinding
import com.philkes.notallyx.databinding.PreferenceSeekbarBinding
import com.philkes.notallyx.databinding.TextInputDialogBinding
import com.philkes.notallyx.presentation.canAuthenticateWithBiometrics
import com.philkes.notallyx.presentation.checkedTag
import com.philkes.notallyx.presentation.setupImportProgressDialog
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.view.misc.TextWithIconAdapter
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.AutoBackup
import com.philkes.notallyx.presentation.viewmodel.preference.AutoBackupPreference
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.BooleanPreference
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.EnumPreference
import com.philkes.notallyx.presentation.viewmodel.preference.IntPreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortPreference
import com.philkes.notallyx.presentation.viewmodel.preference.SortDirection
import com.philkes.notallyx.presentation.viewmodel.preference.StringPreference
import com.philkes.notallyx.presentation.viewmodel.preference.TextProvider
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.encryptDatabase
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt

class SettingsFragment : Fragment() {

    private val model: BaseNoteModel by activityViewModels()
    private lateinit var importBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var importOtherActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportBackupActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var chooseBackupFolderActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var setupLockActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var disableLockActivityResultLauncher: ActivityResultLauncher<Intent>

    private lateinit var selectedImportSource: ImportSource

    private fun setupBinding(binding: FragmentSettingsBinding) {
        model.preferences.apply {
            notesView.observe(viewLifecycleOwner) { value -> binding.View.setup(notesView, value) }

            theme.observe(viewLifecycleOwner) { value -> binding.Theme.setup(theme, value) }

            dateFormat.observe(viewLifecycleOwner) { value ->
                binding.DateFormat.setup(dateFormat, value)
            }

            textSize.observe(viewLifecycleOwner) { value ->
                binding.TextSize.setup(textSize, value)
            }

            notesSorting.observe(viewLifecycleOwner) { notesSort ->
                binding.NotesSortOrder.setup(notesSorting, notesSort)
            }

            // TODO: Hide for now until checked auto-sort is working reliably
            //            listItemSorting.observe(viewLifecycleOwner) { value ->
            //                binding.CheckedListItemSorting.setup(ListItemSorting, value)
            //            }

            binding.MaxLabels.setup(maxLabels)

            binding.MaxItems.setup(maxItems)

            binding.MaxLines.setup(maxLines)

            binding.MaxTitle.setup(maxTitle)

            autoBackup.observe(viewLifecycleOwner) { value ->
                setupAutoBackup(binding, value, autoBackup)
            }

            backupPassword.observe(viewLifecycleOwner) { value ->
                binding.BackupPassword.setupBackupPassword(backupPassword, value)
            }

            biometricLock.observe(viewLifecycleOwner) { value ->
                binding.BiometricLock.setup(biometricLock, value)
            }

            dataOnExternalStorage.observe(viewLifecycleOwner) { value ->
                binding.ExternalDataFolder.setup(
                    dataOnExternalStorage,
                    value,
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

        binding.ImportBackup.setOnClickListener { importBackup() }
        binding.ImportOther.setOnClickListener { importOther() }

        binding.ExportBackup.setOnClickListener { exportBackup() }

        binding.ClearData.setOnClickListener { clearData() }

        model.exportProgress.setupProgressDialog(this, R.string.exporting_backup)
        model.importProgress.setupImportProgressDialog(this, R.string.importing_backup)
        model.deletionProgress.setupProgressDialog(this, R.string.deleting_files)

        binding.SourceCode.setOnClickListener { openLink("https://github.com/PhilKes/NotallyX") }

        binding.Libraries.setOnClickListener { displayLibraries() }

        binding.Rate.setOnClickListener {
            openLink("https://play.google.com/store/apps/details?id=com.philkes.notallyx")
        }

        binding.SendFeedback.setOnClickListener { sendEmailWithLog() }
        binding.CreateIssue.setOnClickListener { createIssue() }

        try {
            val pInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val version = pInfo.versionName
            binding.VersionText.text = "v$version"
        } catch (_: PackageManager.NameNotFoundException) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentSettingsBinding.inflate(inflater)
        setupBinding(binding)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActivityResultLaunchers()
    }

    private fun setupAutoBackup(
        binding: FragmentSettingsBinding,
        value: AutoBackup,
        preference: AutoBackupPreference,
    ) {
        binding.AutoBackupMax.setup(
            value.maxBackups,
            R.string.max_backups,
            AutoBackup.BACKUP_MAX_MIN,
            AutoBackup.BACKUP_MAX_MAX,
        ) { newValue ->
            model.savePreference(preference, preference.value.copy(maxBackups = newValue))
        }
        binding.AutoBackup.setupAutoBackup(value.path)
        binding.AutoBackupPeriodDays.setup(
            value.periodInDays,
            R.string.backup_period_days,
            AutoBackup.BACKUP_PERIOD_DAYS_MIN,
            AutoBackup.BACKUP_PERIOD_DAYS_MAX,
        ) { newValue ->
            model.savePreference(preference, preference.value.copy(periodInDays = newValue))
        }
    }

    private fun setupActivityResultLaunchers() {
        importBackupActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> importBackup(uri) }
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
                    result.data?.data?.let { uri -> model.setAutoBackupPath(uri) }
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
    }

    private fun importBackup(uri: Uri) {
        when (requireContext().contentResolver.getType(uri)) {
            "text/xml" -> {
                model.importXmlBackup(uri)
            }

            "application/zip" -> {
                val layout = TextInputDialogBinding.inflate(layoutInflater, null, false)
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
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun exportBackup() {
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/zip"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, "NotallyX Backup")
            }
        exportBackupActivityResultLauncher.launch(intent)
    }

    private fun importBackup() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "text/xml"))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        importBackupActivityResultLauncher.launch(intent)
    }

    private fun clearData() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.delete_all) { _, _ -> model.deleteAll() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun importOther() {
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
                        val intent =
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                type = "ap/*"
                                putExtra(
                                    Intent.EXTRA_MIME_TYPES,
                                    arrayOf(selectedImportSource.mimeType),
                                )
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                        importOtherActivityResultLauncher.launch(intent)
                    }
                    .setNegativeButton(R.string.help) { _, _ ->
                        val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse(selectedImportSource.documentationUrl)
                            }
                        startActivity(intent)
                    }
                    .setNeutralButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createIssue() {
        MaterialAlertDialogBuilder(requireContext())
        val options =
            arrayOf(getString(R.string.report_bug), getString(R.string.make_feature_request))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_github_issue)
            .setItems(options) { _, which ->
                val intent =
                    when (which) {
                        0 -> {
                            val app = requireContext().applicationContext as Application
                            val logs = Operations.getLastExceptionLog(app)
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/PhilKes/NotallyX/issues/new?labels=bug&projects=&template=bug_report.yml${logs?.let { "&logs=$it" } ?: ""}"
                                        .take(2000)
                                ),
                            )
                        }
                        else ->
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/PhilKes/NotallyX/issues/new?labels=enhancement&template=feature_request.md"
                                ),
                            )
                    }
                try {
                    startActivity(intent)
                } catch (exception: ActivityNotFoundException) {
                    Toast.makeText(requireContext(), R.string.install_a_browser, Toast.LENGTH_LONG)
                        .show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendEmailWithLog() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))

        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("notallyx@yahoo.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "NotallyX [Feedback]")

        val app = requireContext().applicationContext as Application
        val log = Operations.getLog(app)
        if (log.exists()) {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.provider", log)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
        }

        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.install_an_email, Toast.LENGTH_LONG).show()
        }
    }

    private fun displayLibraries() {
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
            )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.libraries)
            .setItems(libraries) { _, which ->
                when (which) {
                    0 -> openLink("https://github.com/bumptech/glide")
                    1 -> openLink("https://github.com/ocpsoft/prettytime")
                    2 -> openLink("https://github.com/zerobranch/SwipeLayout")
                    3 -> openLink("https://developer.android.com/jetpack/androidx/releases/work")
                    4 -> openLink("https://github.com/davemorrissey/subsampling-scale-image-view")
                    5 ->
                        openLink(
                            "https://github.com/material-components/material-components-android"
                        )
                    6 -> openLink("https://github.com/sqlcipher/sqlcipher")
                    7 -> openLink("https://github.com/srikanth-lingala/zip4j")
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun displayChooseBackupFolderDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.notes_will_be)
            .setPositiveButton(R.string.choose_folder) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                chooseBackupFolderActivityResultLauncher.launch(intent)
            }
            .show()
    }

    private inline fun <reified T> PreferenceBinding.setup(
        enumPreference: EnumPreference<T>,
        value: T,
    ) where T : Enum<T>, T : TextProvider {
        Title.setText(enumPreference.titleResId!!)
        val context = requireContext()
        Value.text = value.getText(context)
        val enumEntries = T::class.java.enumConstants!!.toList()
        val entries = enumEntries.map { it.getText(requireContext()) }.toTypedArray()
        val checked = enumEntries.indexOfFirst { it == value }
        root.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(enumPreference.titleResId)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    dialog.cancel()
                    val newValue = enumEntries[which]
                    model.savePreference(enumPreference, newValue)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun PreferenceBinding.setup(
        preference: EnumPreference<BiometricLock>,
        value: BiometricLock,
    ) {
        Title.setText(preference.titleResId!!)

        val context = requireContext()
        Value.text = value.getText(context)
        val enumEntries = BiometricLock.entries
        val entries = enumEntries.map { context.getString(it.textResId) }.toTypedArray()
        val checked = enumEntries.indexOfFirst { it == value }

        root.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.titleResId)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    dialog.cancel()
                    val newValue = enumEntries[which]
                    if (newValue == BiometricLock.ENABLED) {
                        when (requireContext().canAuthenticateWithBiometrics()) {
                            BiometricManager.BIOMETRIC_SUCCESS -> showEnableBiometricLock()
                            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                                showNoBiometricsSupportToast()

                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                showBiometricsNotSetupDialog()
                        }
                    } else {
                        when (requireContext().canAuthenticateWithBiometrics()) {
                            BiometricManager.BIOMETRIC_SUCCESS -> showDisableBiometricLock()
                            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                                showNoBiometricsSupportToast()
                                model.savePreference(
                                    model.preferences.biometricLock,
                                    BiometricLock.DISABLED,
                                )
                            }

                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                showBiometricsNotSetupDialog()
                                model.savePreference(
                                    model.preferences.biometricLock,
                                    BiometricLock.DISABLED,
                                )
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun PreferenceBinding.setup(preference: NotesSortPreference, value: NotesSort) {
        Title.setText(preference.titleResId!!)

        Value.text = value.getText(requireContext())

        root.setOnClickListener {
            val layout = NotesSortDialogBinding.inflate(layoutInflater, null, false)
            NotesSortBy.entries.forEachIndexed { idx, notesSortBy ->
                ChoiceItemBinding.inflate(layoutInflater).root.apply {
                    id = idx
                    text = requireContext().getString(notesSortBy.textResId)
                    tag = notesSortBy
                    layout.NotesSortByRadioGroup.addView(this)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(notesSortBy.iconResId, 0, 0, 0)
                    if (notesSortBy == value.sortedBy) {
                        layout.NotesSortByRadioGroup.check(this.id)
                    }
                }
            }

            SortDirection.entries.forEachIndexed { idx, sortDir ->
                ChoiceItemBinding.inflate(layoutInflater).root.apply {
                    id = idx
                    text = requireContext().getString(sortDir.textResId)
                    tag = sortDir
                    setCompoundDrawablesRelativeWithIntrinsicBounds(sortDir.iconResId, 0, 0, 0)
                    layout.NotesSortDirectionRadioGroup.addView(this)
                    if (sortDir == value.sortDirection) {
                        layout.NotesSortDirectionRadioGroup.check(this.id)
                    }
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.titleResId)
                .setView(layout.root)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    dialog.cancel()
                    val newSortBy = layout.NotesSortByRadioGroup.checkedTag() as NotesSortBy
                    val newSortDirection =
                        layout.NotesSortDirectionRadioGroup.checkedTag() as SortDirection
                    model.savePreference(
                        model.preferences.notesSorting,
                        NotesSort(newSortBy, newSortDirection),
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun PreferenceBinding.setup(
        preference: BooleanPreference,
        value: Boolean,
        messageResId: Int? = null,
        onSave: ((newValue: Boolean) -> Unit)?,
    ) {
        Title.setText(preference.titleResId!!)

        val context = requireContext()
        val enabledText = context.getString(R.string.enabled)
        val disabledText = context.getString(R.string.disabled)
        Value.text = if (value) enabledText else disabledText
        root.setOnClickListener {
            val layout =
                PreferenceBooleanDialogBinding.inflate(layoutInflater, null, false).apply {
                    Title.setText(preference.titleResId)
                    messageResId?.let { Message.setText(it) }
                    if (value) {
                        EnabledButton.isChecked = true
                    } else {
                        DisabledButton.isChecked = true
                    }
                }
            val dialog =
                MaterialAlertDialogBuilder(requireContext())
                    .setView(layout.root)
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            layout.apply {
                EnabledButton.setOnClickListener {
                    dialog.cancel()
                    if (!value) {
                        onSave?.invoke(true) ?: model.savePreference(preference, true)
                    }
                }
                DisabledButton.setOnClickListener {
                    dialog.cancel()
                    if (value) {
                        onSave?.invoke(false) ?: model.savePreference(preference, false)
                    }
                }
            }
        }
    }

    private fun PreferenceBinding.setupBackupPassword(
        preference: StringPreference,
        password: String,
    ) {
        Title.setText(preference.titleResId!!)

        Value.transformationMethod =
            if (password != PASSWORD_EMPTY) PasswordTransformationMethod.getInstance() else null
        Value.text = if (password != PASSWORD_EMPTY) password else getText(R.string.tap_to_set_up)
        root.setOnClickListener {
            val layout = TextInputDialogBinding.inflate(layoutInflater, null, false)
            layout.InputText.apply {
                if (password != PASSWORD_EMPTY) {
                    setText(password)
                }
                transformationMethod = PasswordTransformationMethod.getInstance()
            }
            layout.InputTextLayout.endIconMode = END_ICON_PASSWORD_TOGGLE
            layout.Message.apply {
                setText(R.string.backup_password_hint)
                visibility = View.VISIBLE
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.titleResId)
                .setView(layout.root)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    dialog.cancel()
                    val updatedPassword = layout.InputText.text.toString()
                    model.savePreference(preference, updatedPassword)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.clear) { dialog, _ ->
                    dialog.cancel()
                    model.savePreference(preference, PASSWORD_EMPTY)
                }
                .show()
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
                    model.savePreference(model.preferences.biometricLock, BiometricLock.ENABLED)
                }
                val app = (activity?.application as NotallyXApplication)
                app.locked.value = false
                showBiometricsEnabledToast()
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
                    val encryptedPassphrase = model.preferences.databaseEncryptionKey.value
                    val passphrase = cipher.doFinal(encryptedPassphrase)
                    model.closeDatabase()
                    decryptDatabase(requireContext(), passphrase)
                    model.savePreference(model.preferences.biometricLock, BiometricLock.DISABLED)
                }
                showBiometricsDisabledToast()
            },
        ) {}
    }

    private fun showNoBiometricsSupportToast() {
        ContextCompat.getMainExecutor(requireContext()).execute {
            Toast.makeText(requireContext(), R.string.biometrics_setup_success, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun showBiometricsEnabledToast() {
        ContextCompat.getMainExecutor(requireContext()).execute {
            Toast.makeText(requireContext(), R.string.biometrics_setup_success, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun showBiometricsDisabledToast() {
        ContextCompat.getMainExecutor(requireContext()).execute {
            Toast.makeText(requireContext(), R.string.biometrics_disable_success, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun showBiometricsNotSetupDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.biometrics_not_setup)
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setPositiveButton(R.string.tap_to_set_up) { _, _ ->
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
            .show()
    }

    private fun PreferenceBinding.setupAutoBackup(value: String) {
        Title.setText(R.string.auto_backup)

        if (value == EMPTY_PATH) {
            Value.setText(R.string.tap_to_set_up)

            root.setOnClickListener { displayChooseBackupFolderDialog() }
        } else {
            val uri = Uri.parse(value)
            val folder = requireNotNull(DocumentFile.fromTreeUri(requireContext(), uri))
            if (folder.exists()) {
                Value.text = folder.name
            } else Value.setText(R.string.cant_find_folder)

            root.setOnClickListener {
                MenuDialog(requireContext())
                    .add(R.string.disable_auto_backup) { model.disableAutoBackup() }
                    .add(R.string.choose_another_folder) { displayChooseBackupFolderDialog() }
                    .show()
            }
        }
    }

    private fun PreferenceSeekbarBinding.setup(
        preference: IntPreference,
        value: Int = preference.value,
    ) {
        setup(value, preference.titleResId!!, preference.min, preference.max) { newValue ->
            model.savePreference(preference, newValue)
        }
    }

    private fun PreferenceSeekbarBinding.setup(
        value: Int,
        titleResId: Int,
        min: Int,
        max: Int,
        onChange: (newValue: Int) -> Unit,
    ) {
        Title.setText(titleResId)

        Slider.apply {
            valueTo = max.toFloat()
            valueFrom = min.toFloat()
            this@apply.value = value.toFloat()
            addOnChangeListener { _, value, _ -> onChange(value.toInt()) }
            contentDescription = getString(titleResId)
        }
    }

    private fun openLink(link: String) {
        val uri = Uri.parse(link)
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.install_a_browser, Toast.LENGTH_LONG).show()
        }
    }
}
