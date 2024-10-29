package com.philkes.notallyx.presentation.activity.main.fragment

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.NotallyXApplication
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ChoiceItemBinding
import com.philkes.notallyx.databinding.FragmentSettingsBinding
import com.philkes.notallyx.databinding.NotesSortDialogBinding
import com.philkes.notallyx.databinding.PreferenceBinding
import com.philkes.notallyx.databinding.PreferenceSeekbarBinding
import com.philkes.notallyx.databinding.TextInputDialogBinding
import com.philkes.notallyx.presentation.canAuthenticateWithBiometrics
import com.philkes.notallyx.presentation.checkedTag
import com.philkes.notallyx.presentation.setupProgressDialog
import com.philkes.notallyx.presentation.view.misc.AutoBackup
import com.philkes.notallyx.presentation.view.misc.AutoBackupMax
import com.philkes.notallyx.presentation.view.misc.AutoBackupPeriodDays
import com.philkes.notallyx.presentation.view.misc.BackupPassword
import com.philkes.notallyx.presentation.view.misc.BackupPassword.emptyPassword
import com.philkes.notallyx.presentation.view.misc.BiometricLock
import com.philkes.notallyx.presentation.view.misc.BiometricLock.disabled
import com.philkes.notallyx.presentation.view.misc.BiometricLock.enabled
import com.philkes.notallyx.presentation.view.misc.DateFormat
import com.philkes.notallyx.presentation.view.misc.ListInfo
import com.philkes.notallyx.presentation.view.misc.MaxItems
import com.philkes.notallyx.presentation.view.misc.MaxLines
import com.philkes.notallyx.presentation.view.misc.MaxTitle
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.view.misc.NotesSorting
import com.philkes.notallyx.presentation.view.misc.SeekbarInfo
import com.philkes.notallyx.presentation.view.misc.SortDirection
import com.philkes.notallyx.presentation.view.misc.TextSize
import com.philkes.notallyx.presentation.view.misc.Theme
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.utils.Operations
import com.philkes.notallyx.utils.backup.Export.scheduleAutoBackup
import com.philkes.notallyx.utils.security.decryptDatabase
import com.philkes.notallyx.utils.security.encryptDatabase
import com.philkes.notallyx.utils.security.showBiometricOrPinPrompt

class SettingsFragment : Fragment() {

    private val model: BaseNoteModel by activityViewModels()

    private fun setupBinding(binding: FragmentSettingsBinding) {
        model.preferences.apply {
            view.observe(viewLifecycleOwner) { value ->
                binding.View.setup(com.philkes.notallyx.presentation.view.misc.View, value)
            }

            theme.observe(viewLifecycleOwner) { value -> binding.Theme.setup(Theme, value) }

            dateFormat.observe(viewLifecycleOwner) { value ->
                binding.DateFormat.setup(DateFormat, value)
            }

            textSize.observe(viewLifecycleOwner) { value ->
                binding.TextSize.setup(TextSize, value)
            }

            notesSorting.observe(viewLifecycleOwner) { (sortBy, sortDirection) ->
                binding.NotesSortOrder.setup(NotesSorting, sortBy, sortDirection)
            }

            // TODO: Hide for now until checked auto-sort is working reliably
            //            listItemSorting.observe(viewLifecycleOwner) { value ->
            //                binding.CheckedListItemSorting.setup(ListItemSorting, value)
            //            }

            binding.MaxItems.setup(MaxItems, maxItems)

            binding.MaxLines.setup(MaxLines, maxLines)

            binding.MaxTitle.setup(MaxTitle, maxTitle)

            binding.AutoBackupMax.setup(AutoBackupMax, autoBackupMax)

            autoBackupPath.observe(viewLifecycleOwner) { value ->
                binding.AutoBackup.setup(AutoBackup, value)
            }

            autoBackupPeriodDays.observe(viewLifecycleOwner) { value ->
                binding.AutoBackupPeriodDays.setup(AutoBackupPeriodDays, value)
                scheduleAutoBackup(value.toLong(), requireContext())
            }

            backupPassword.observe(viewLifecycleOwner) { value ->
                binding.BackupPassword.setup(BackupPassword, value)
            }

            biometricLock.observe(viewLifecycleOwner) { value ->
                binding.BiometricLock.setup(BiometricLock, value)
            }
        }

        binding.ImportBackup.setOnClickListener { importBackup() }

        binding.ExportBackup.setOnClickListener { exportBackup() }

        binding.ClearData.setOnClickListener { clearData() }

        model.exportProgress.setupProgressDialog(this, R.string.exporting_backup)
        model.importProgress.setupProgressDialog(this, R.string.importing_backup)
        model.deletionProgress.setupProgressDialog(this, R.string.deleting_files)

        binding.GitHub.setOnClickListener { openLink("https://github.com/PhilKes/NotallyX") }

        binding.Libraries.setOnClickListener { displayLibraries() }

        binding.Rate.setOnClickListener {
            openLink("https://play.google.com/store/apps/details?id=com.philkes.notallyx")
        }

        binding.SendFeedback.setOnClickListener { sendFeedback() }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            intent?.data?.let { uri ->
                when (requestCode) {
                    REQUEST_IMPORT_BACKUP -> importBackup(uri)
                    REQUEST_EXPORT_BACKUP -> model.exportBackup(uri)
                    REQUEST_CHOOSE_FOLDER -> model.setAutoBackupPath(uri)
                }
                return
            }
        }
        when (requestCode) {
            REQUEST_SETUP_LOCK -> showEnableBiometricLock()
            REQUEST_DISABLE_LOCK -> showDisableBiometricLock()
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
                    if (password != emptyPassword) {
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
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP)
    }

    private fun importBackup() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "text/xml"))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        startActivityForResult(intent, REQUEST_IMPORT_BACKUP)
    }

    private fun clearData() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.clear_data_message)
            .setPositiveButton(R.string.delete_all) { _, _ -> model.deleteAllBaseNotes() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun sendFeedback() {
        MaterialAlertDialogBuilder(requireContext())
        val options =
            arrayOf(getString(R.string.report_bug), getString(R.string.make_feature_request))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.send_feedback)
            .setItems(options) { _, which ->
                val intent =
                    when (which) {
                        0 -> {
                            val app = requireContext().applicationContext as Application
                            val logs = Operations.getLastExceptionLog(app)
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://github.com/PhilKes/NotallyX/issues/new?labels=bug&projects=&template=bug_report.yml&logs=$logs"
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

    private fun displayChooseFolderDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.notes_will_be)
            .setPositiveButton(R.string.choose_folder) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, REQUEST_CHOOSE_FOLDER)
            }
            .show()
    }

    private fun PreferenceBinding.setup(info: ListInfo, value: String) {
        Title.setText(info.title)

        val entries = info.getEntries(requireContext())
        val entryValues = info.getEntryValues()

        val checked = entryValues.indexOf(value)
        val displayValue = entries[checked]

        Value.text = displayValue

        root.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(info.title)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    dialog.cancel()
                    val newValue = entryValues[which]
                    model.savePreference(info, newValue)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun PreferenceBinding.setup(
        info: NotesSorting,
        sortBy: String,
        sortDirection: SortDirection,
    ) {
        Title.setText(info.title)

        val entries = info.getEntries(requireContext())
        val entryValues = info.getEntryValues()

        val checked = entryValues.indexOf(sortBy)
        val displayValue = entries[checked]

        Value.text = "$displayValue (${requireContext().getString(sortDirection.textResId)})"

        root.setOnClickListener {
            val layout = NotesSortDialogBinding.inflate(layoutInflater, null, false)
            entries.zip(entryValues).forEachIndexed { idx, (choiceText, sortByValue) ->
                ChoiceItemBinding.inflate(layoutInflater).root.apply {
                    id = idx
                    text = choiceText
                    tag = sortByValue
                    layout.NotesSortByRadioGroup.addView(this)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        NotesSorting.getSortIconResId(sortByValue),
                        0,
                        0,
                        0,
                    )
                    if (sortByValue == sortBy) {
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
                    if (sortDir == sortDirection) {
                        layout.NotesSortDirectionRadioGroup.check(this.id)
                    }
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(info.title)
                .setView(layout.root)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    dialog.cancel()
                    val newSortBy = layout.NotesSortByRadioGroup.checkedTag() as String
                    val newSortDirection =
                        layout.NotesSortDirectionRadioGroup.checkedTag() as SortDirection
                    model.preferences.savePreference(info, newSortBy, newSortDirection)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun PreferenceBinding.setup(info: BackupPassword, password: String) {
        Title.setText(info.title)

        Value.transformationMethod =
            if (password != emptyPassword) PasswordTransformationMethod.getInstance() else null
        Value.text = password
        root.setOnClickListener {
            val layout = TextInputDialogBinding.inflate(layoutInflater, null, false)
            layout.InputText.apply {
                if (password != emptyPassword) {
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
                .setTitle(info.title)
                .setView(layout.root)
                .setPositiveButton(R.string.save) { dialog, _ ->
                    dialog.cancel()
                    val updatedPassword = layout.InputText.text.toString()
                    model.preferences.savePreference(info, updatedPassword)
                }
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.clear) { dialog, _ ->
                    dialog.cancel()
                    model.preferences.savePreference(info, emptyPassword)
                }
                .show()
        }
    }

    private fun PreferenceBinding.setup(info: BiometricLock, value: String) {
        Title.setText(info.title)

        val entries = info.getEntries(requireContext())
        val entryValues = info.getEntryValues()

        val checked = entryValues.indexOf(value)
        val displayValue = entries[checked]

        Value.text = displayValue

        root.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(info.title)
                .setSingleChoiceItems(entries, checked) { dialog, which ->
                    dialog.cancel()
                    val newValue = entryValues[which]
                    if (newValue == enabled) {
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
                                model.preferences.biometricLock.value = disabled
                            }
                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                                showBiometricsNotSetupDialog()
                                model.preferences.biometricLock.value = disabled
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showEnableBiometricLock() {
        showBiometricOrPinPrompt(
            false,
            REQUEST_SETUP_LOCK,
            R.string.enable_lock_title,
            R.string.enable_lock_description,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    model.preferences.iv = cipher.iv
                    val passphrase = model.preferences.generatePassphrase(cipher)
                    encryptDatabase(requireContext(), passphrase)
                    model.savePreference(BiometricLock, enabled)
                }
                (activity?.application as NotallyXApplication).isLocked = false
                showBiometricsEnabledToast()
            },
        ) {
            showBiometricsNotSetupDialog()
        }
    }

    private fun showDisableBiometricLock() {
        showBiometricOrPinPrompt(
            true,
            REQUEST_DISABLE_LOCK,
            R.string.disable_lock_title,
            R.string.disable_lock_description,
            model.preferences.iv!!,
            onSuccess = { cipher ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val encryptedPassphrase = model.preferences.getDatabasePassphrase()
                    val passphrase = cipher.doFinal(encryptedPassphrase)
                    model.closeDatabase()
                    decryptDatabase(requireContext(), passphrase)
                    model.savePreference(BiometricLock, disabled)
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
                startActivityForResult(intent, REQUEST_SETUP_LOCK)
            }
            .show()
    }

    private fun PreferenceBinding.setup(info: AutoBackup, value: String) {
        Title.setText(info.title)

        if (value == info.emptyPath) {
            Value.setText(R.string.tap_to_set_up)

            root.setOnClickListener { displayChooseFolderDialog() }
        } else {
            val uri = Uri.parse(value)
            val folder = requireNotNull(DocumentFile.fromTreeUri(requireContext(), uri))
            if (folder.exists()) {
                Value.text = folder.name
            } else Value.setText(R.string.cant_find_folder)

            root.setOnClickListener {
                MenuDialog(requireContext())
                    .add(R.string.disable_auto_backup) { model.disableAutoBackup() }
                    .add(R.string.choose_another_folder) { displayChooseFolderDialog() }
                    .show()
            }
        }
    }

    private fun PreferenceSeekbarBinding.setup(info: SeekbarInfo, initialValue: Int) {
        Title.setText(info.title)

        Slider.apply {
            valueTo = info.max.toFloat()
            valueFrom = info.min.toFloat()
            value = initialValue.toFloat()
            addOnChangeListener { _, value, _ -> model.savePreference(info, value.toInt()) }
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

    companion object {
        private const val REQUEST_IMPORT_BACKUP = 20
        private const val REQUEST_EXPORT_BACKUP = 21
        private const val REQUEST_CHOOSE_FOLDER = 22
        private const val REQUEST_SETUP_LOCK = 23
        private const val REQUEST_DISABLE_LOCK = 24
    }
}
