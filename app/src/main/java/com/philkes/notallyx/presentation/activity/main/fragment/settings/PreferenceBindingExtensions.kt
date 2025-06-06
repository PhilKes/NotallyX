package com.philkes.notallyx.presentation.activity.main.fragment.settings

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.ChoiceItemBinding
import com.philkes.notallyx.databinding.DialogNotesSortBinding
import com.philkes.notallyx.databinding.DialogPreferenceBooleanBinding
import com.philkes.notallyx.databinding.DialogPreferenceEnumWithToggleBinding
import com.philkes.notallyx.databinding.DialogSelectionBoxBinding
import com.philkes.notallyx.databinding.DialogTextInputBinding
import com.philkes.notallyx.databinding.PreferenceBinding
import com.philkes.notallyx.databinding.PreferenceSeekbarBinding
import com.philkes.notallyx.presentation.checkedTag
import com.philkes.notallyx.presentation.select
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.BooleanPreference
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.DateFormat
import com.philkes.notallyx.presentation.viewmodel.preference.EnumPreference
import com.philkes.notallyx.presentation.viewmodel.preference.IntPreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_DEFAULT
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.START_VIEW_UNLABELED
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortPreference
import com.philkes.notallyx.presentation.viewmodel.preference.SortDirection
import com.philkes.notallyx.presentation.viewmodel.preference.StringPreference
import com.philkes.notallyx.presentation.viewmodel.preference.TextProvider
import com.philkes.notallyx.presentation.viewmodel.preference.Theme
import com.philkes.notallyx.utils.canAuthenticateWithBiometrics
import com.philkes.notallyx.utils.toReadablePath

inline fun <reified T> PreferenceBinding.setup(
    enumPreference: EnumPreference<T>,
    value: T,
    context: Context,
    crossinline onSave: (newValue: T) -> Unit,
) where T : Enum<T>, T : TextProvider {
    Title.setText(enumPreference.titleResId!!)
    Value.text = value.getText(context)
    val enumEntries = T::class.java.enumConstants!!.toList()
    val entries = enumEntries.map { it.getText(context) }.toTypedArray()
    val checked = enumEntries.indexOfFirst { it == value }
    root.setOnClickListener {
        MaterialAlertDialogBuilder(context)
            .setTitle(enumPreference.titleResId)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                dialog.cancel()
                val newValue = enumEntries[which]
                onSave(newValue)
            }
            .setCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    preference: EnumPreference<BiometricLock>,
    value: BiometricLock,
    context: Context,
    model: BaseNoteModel,
    onEnableSuccess: () -> Unit,
    onDisableSuccess: () -> Unit,
    onNotSetup: () -> Unit,
) {
    Title.setText(preference.titleResId!!)

    Value.text = value.getText(context)
    val enumEntries = BiometricLock.entries
    val entries = enumEntries.map { context.getString(it.textResId) }.toTypedArray()
    val checked = enumEntries.indexOfFirst { it == value }

    root.setOnClickListener {
        MaterialAlertDialogBuilder(context)
            .setTitle(preference.titleResId)
            .setSingleChoiceItems(entries, checked) { dialog, which ->
                dialog.cancel()
                val newValue = enumEntries[which]
                if (newValue == value) {
                    return@setSingleChoiceItems
                }
                if (newValue == BiometricLock.ENABLED) {
                    when (context.canAuthenticateWithBiometrics()) {
                        BiometricManager.BIOMETRIC_SUCCESS -> onEnableSuccess()
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                            context.showToast(R.string.biometrics_no_support)
                        }

                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> onNotSetup()
                    }
                } else {
                    when (context.canAuthenticateWithBiometrics()) {
                        BiometricManager.BIOMETRIC_SUCCESS -> onDisableSuccess()
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                            context.showToast(R.string.biometrics_no_support)
                            model.savePreference(
                                model.preferences.biometricLock,
                                BiometricLock.DISABLED,
                            )
                        }

                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                            onNotSetup()
                            model.savePreference(
                                model.preferences.biometricLock,
                                BiometricLock.DISABLED,
                            )
                        }
                    }
                }
            }
            .setCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    preference: NotesSortPreference,
    value: NotesSort,
    context: Context,
    layoutInflater: LayoutInflater,
    model: BaseNoteModel,
) {
    Title.setText(preference.titleResId!!)

    Value.text = value.getText(context)

    root.setOnClickListener {
        val layout = DialogNotesSortBinding.inflate(layoutInflater, null, false)
        NotesSortBy.entries.forEachIndexed { idx, notesSortBy ->
            ChoiceItemBinding.inflate(layoutInflater).root.apply {
                id = idx
                text = context.getString(notesSortBy.textResId)
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
                text = context.getString(sortDir.textResId)
                tag = sortDir
                setCompoundDrawablesRelativeWithIntrinsicBounds(sortDir.iconResId, 0, 0, 0)
                layout.NotesSortDirectionRadioGroup.addView(this)
                if (sortDir == value.sortDirection) {
                    layout.NotesSortDirectionRadioGroup.check(this.id)
                }
            }
        }

        MaterialAlertDialogBuilder(context)
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
            .setCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    dateFormatPreference: EnumPreference<DateFormat>,
    dateFormatValue: DateFormat,
    applyToNoteViewValue: Boolean,
    context: Context,
    layoutInflater: LayoutInflater,
    onSave: (dateFormat: DateFormat, applyToEditMode: Boolean) -> Unit,
) {
    Title.setText(dateFormatPreference.titleResId!!)

    Value.text = dateFormatValue.getText(context)

    root.setOnClickListener {
        val layout = DialogPreferenceEnumWithToggleBinding.inflate(layoutInflater, null, false)
        layout.EnumHint.apply {
            setText(R.string.date_format_hint)
            isVisible = true
        }
        DateFormat.entries.forEachIndexed { idx, dateFormat ->
            ChoiceItemBinding.inflate(layoutInflater).root.apply {
                id = idx
                text = dateFormat.getText(context)
                tag = dateFormat
                layout.EnumRadioGroup.addView(this)
                if (dateFormat == dateFormatValue) {
                    layout.EnumRadioGroup.check(this.id)
                }
            }
        }

        layout.Toggle.apply {
            setText(R.string.date_format_apply_in_note_view)
            isChecked = applyToNoteViewValue
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(dateFormatPreference.titleResId)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.cancel()
                val dateFormat = layout.EnumRadioGroup.checkedTag() as DateFormat
                val applyToNoteView = layout.Toggle.isChecked
                onSave(dateFormat, applyToNoteView)
            }
            .setCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    themePreference: EnumPreference<Theme>,
    themeValue: Theme,
    useDynamicColorsValue: Boolean,
    context: Context,
    layoutInflater: LayoutInflater,
    onSave: (theme: Theme, useDynamicColors: Boolean) -> Unit,
) {
    Title.setText(themePreference.titleResId!!)

    Value.text = themeValue.getText(context)

    root.setOnClickListener {
        val layout = DialogPreferenceEnumWithToggleBinding.inflate(layoutInflater, null, false)
        Theme.entries.forEachIndexed { idx, theme ->
            ChoiceItemBinding.inflate(layoutInflater).root.apply {
                id = idx
                text = theme.getText(context)
                tag = theme
                layout.EnumRadioGroup.addView(this)
                if (theme == themeValue) {
                    layout.EnumRadioGroup.check(this.id)
                }
            }
        }

        layout.Toggle.apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setText(R.string.theme_use_dynamic_colors)
            isChecked = useDynamicColorsValue
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(themePreference.titleResId)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.cancel()
                val theme = layout.EnumRadioGroup.checkedTag() as Theme
                val useDynamicColors = layout.Toggle.isChecked
                onSave(theme, useDynamicColors)
            }
            .setCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    preference: BooleanPreference,
    value: Boolean,
    context: Context,
    layoutInflater: LayoutInflater,
    messageResId: Int? = null,
    enabled: Boolean = true,
    disabledTextResId: Int? = null,
    onSave: (newValue: Boolean) -> Unit,
) {
    Title.setText(preference.titleResId!!)

    if (enabled) {
        Value.setText(if (value) R.string.enabled else R.string.disabled)
    } else {
        disabledTextResId?.let { Value.setText(it) }
    }
    root.isEnabled = enabled
    root.setOnClickListener {
        val layout =
            DialogPreferenceBooleanBinding.inflate(layoutInflater, null, false).apply {
                Title.setText(preference.titleResId)
                messageResId?.let { Message.setText(it) }
                if (value) {
                    EnabledButton.isChecked = true
                } else {
                    DisabledButton.isChecked = true
                }
            }
        val dialog =
            MaterialAlertDialogBuilder(context).setView(layout.root).setCancelButton().show()
        layout.apply {
            EnabledButton.setOnClickListener {
                dialog.cancel()
                if (!value) {
                    onSave.invoke(true)
                }
            }
            DisabledButton.setOnClickListener {
                dialog.cancel()
                if (value) {
                    onSave.invoke(false)
                }
            }
        }
    }
}

fun PreferenceBinding.setupPeriodicBackup(
    value: Boolean,
    context: Context,
    layoutInflater: LayoutInflater,
    enabled: Boolean,
    onSave: (newValue: Boolean) -> Unit,
) {
    Title.setText(R.string.backup_periodic)
    val enabledText = context.getString(R.string.enabled)
    val disabledText = context.getString(R.string.disabled)
    val text =
        if (enabled) {
            if (value) enabledText else disabledText
        } else context.getString(R.string.auto_backups_folder_set)
    Value.text = text
    root.isEnabled = enabled
    root.setOnClickListener {
        val layout =
            DialogPreferenceBooleanBinding.inflate(layoutInflater, null, false).apply {
                Title.setText(R.string.backup_periodic)
                Message.setText(R.string.backup_periodic_hint)
                if (value) {
                    EnabledButton.isChecked = true
                } else {
                    DisabledButton.isChecked = true
                }
            }
        val dialog =
            MaterialAlertDialogBuilder(context).setView(layout.root).setCancelButton().show()
        layout.apply {
            EnabledButton.setOnClickListener {
                dialog.cancel()
                if (!value) {
                    onSave.invoke(true)
                }
            }
            DisabledButton.setOnClickListener {
                dialog.cancel()
                if (value) {
                    onSave.invoke(false)
                }
            }
        }
    }
}

fun PreferenceBinding.setupBackupPassword(
    preference: StringPreference,
    password: String,
    context: Context,
    layoutInflater: LayoutInflater,
    onSave: (newValue: String) -> Unit,
) {
    Title.setText(preference.titleResId!!)

    Value.transformationMethod =
        if (password != PASSWORD_EMPTY) PasswordTransformationMethod.getInstance() else null
    Value.text =
        if (password != PASSWORD_EMPTY) password else context.getText(R.string.tap_to_set_up)
    root.setOnClickListener {
        val layout = DialogTextInputBinding.inflate(layoutInflater, null, false)
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
        MaterialAlertDialogBuilder(context)
            .setTitle(preference.titleResId)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.cancel()
                val updatedPassword = layout.InputText.text.toString()
                onSave(updatedPassword)
            }
            .setCancelButton()
            .setNeutralButton(R.string.clear) { dialog, _ ->
                dialog.cancel()
                onSave(PASSWORD_EMPTY)
            }
            .showAndFocus(allowFullSize = true)
    }
}

fun PreferenceBinding.setupBackupsFolder(
    value: String,
    context: Context,
    chooseBackupFolder: () -> Unit,
    onDisable: () -> Unit,
) {
    Title.setText(R.string.auto_backups_folder)

    if (value == EMPTY_PATH) {
        Value.setText(R.string.tap_to_set_up)

        root.setOnClickListener { chooseBackupFolder() }
    } else {
        val uri = Uri.parse(value)
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        if (folder.exists()) {
            val path = uri.toReadablePath()
            Value.text = path
        } else Value.setText(R.string.cant_find_folder)

        root.setOnClickListener {
            MenuDialog(context)
                .add(R.string.clear) { onDisable() }
                .add(R.string.choose_another_folder) { chooseBackupFolder() }
                .show()
        }
    }
}

fun PreferenceSeekbarBinding.setup(
    value: Int,
    titleResId: Int,
    min: Int,
    max: Int,
    context: Context,
    enabled: Boolean = true,
    onChange: (newValue: Int) -> Unit,
) {
    Title.setText(titleResId)
    val valueInBoundaries = (if (value < min) min else if (value > max) max else value).toFloat()
    Slider.apply {
        isEnabled = enabled
        valueTo = max.toFloat()
        valueFrom = min.toFloat()
        this@apply.value = valueInBoundaries
        clearOnSliderTouchListeners()
        addOnSliderTouchListener(
            object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}

                override fun onStopTrackingTouch(slider: Slider) {
                    onChange(slider.value.toInt())
                }
            }
        )
        contentDescription = context.getString(titleResId)
    }
}

fun PreferenceSeekbarBinding.setup(
    preference: IntPreference,
    context: Context,
    value: Int = preference.value,
    onChange: (newValue: Int) -> Unit,
) {
    setup(value, preference.titleResId!!, preference.min, preference.max, context) { newValue ->
        onChange(newValue)
    }
}

fun PreferenceSeekbarBinding.setupAutoSaveIdleTime(
    preference: IntPreference,
    context: Context,
    value: Int = preference.value,
    onChange: (newValue: Int) -> Unit,
) {
    Slider.apply {
        setLabelFormatter { sliderValue ->
            if (sliderValue == -1f) {
                context.getString(R.string.disabled)
            } else "${sliderValue.toInt()}s"
        }
        addOnChangeListener { _, value, _ ->
            if (value == -1f) {
                setAlpha(0.6f) // Reduce opacity to make it look disabled
            } else {
                setAlpha(1f) // Restore normal appearance
            }
        }
    }
    setup(preference, context, value, onChange)
}

fun PreferenceBinding.setupStartView(
    preference: StringPreference,
    value: String,
    labels: List<String>?,
    context: Context,
    layoutInflater: LayoutInflater,
    onSave: (value: String) -> Unit,
) {
    Title.setText(preference.titleResId!!)

    val notesText = "${context.getText(R.string.notes)} (${context.getText(R.string.text_default)})"
    val unlabeledText = context.getText(R.string.unlabeled).toString()
    val textValue =
        when (value) {
            START_VIEW_DEFAULT -> notesText
            START_VIEW_UNLABELED -> unlabeledText
            else -> value
        }
    Value.text = textValue

    root.setOnClickListener {
        val layout = DialogSelectionBoxBinding.inflate(layoutInflater, null, false)
        layout.Message.setText(R.string.start_view_hint)
        val values =
            mutableListOf(notesText to START_VIEW_DEFAULT, unlabeledText to START_VIEW_UNLABELED)
                .apply { labels?.forEach { add(it to it) } }
        var selected = -1
        layout.SelectionBox.apply {
            setSimpleItems(values.map { it.first }.toTypedArray())
            select(textValue)
            setOnItemClickListener { _, _, position, _ -> selected = position }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(preference.titleResId)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.cancel()
                val newValue = values[selected].second
                onSave(newValue)
            }
            .setCancelButton()
            .showAndFocus(allowFullSize = true)
    }
}
