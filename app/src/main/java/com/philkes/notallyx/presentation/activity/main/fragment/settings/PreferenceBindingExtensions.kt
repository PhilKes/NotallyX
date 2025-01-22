package com.philkes.notallyx.presentation.activity.main.fragment.settings

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.toText
import com.philkes.notallyx.databinding.ChoiceItemBinding
import com.philkes.notallyx.databinding.NotesSortDialogBinding
import com.philkes.notallyx.databinding.PreferenceBinding
import com.philkes.notallyx.databinding.PreferenceBooleanDialogBinding
import com.philkes.notallyx.databinding.PreferenceSeekbarBinding
import com.philkes.notallyx.databinding.TextInputDialogBinding
import com.philkes.notallyx.presentation.addCancelButton
import com.philkes.notallyx.presentation.checkedTag
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.BiometricLock
import com.philkes.notallyx.presentation.viewmodel.preference.BooleanPreference
import com.philkes.notallyx.presentation.viewmodel.preference.Constants.PASSWORD_EMPTY
import com.philkes.notallyx.presentation.viewmodel.preference.EnumPreference
import com.philkes.notallyx.presentation.viewmodel.preference.IntPreference
import com.philkes.notallyx.presentation.viewmodel.preference.LongPreference
import com.philkes.notallyx.presentation.viewmodel.preference.NotallyXPreferences.Companion.EMPTY_PATH
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSort
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortPreference
import com.philkes.notallyx.presentation.viewmodel.preference.SortDirection
import com.philkes.notallyx.presentation.viewmodel.preference.StringPreference
import com.philkes.notallyx.presentation.viewmodel.preference.TextProvider
import com.philkes.notallyx.utils.canAuthenticateWithBiometrics
import com.philkes.notallyx.utils.toReadablePath
import java.util.Date

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
            .addCancelButton()
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
            .addCancelButton()
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
        val layout = NotesSortDialogBinding.inflate(layoutInflater, null, false)
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
            .addCancelButton()
            .show()
    }
}

fun PreferenceBinding.setup(
    preference: BooleanPreference,
    value: Boolean,
    context: Context,
    layoutInflater: LayoutInflater,
    messageResId: Int? = null,
    onSave: (newValue: Boolean) -> Unit,
) {
    Title.setText(preference.titleResId!!)

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
            MaterialAlertDialogBuilder(context).setView(layout.root).addCancelButton().show()
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
        MaterialAlertDialogBuilder(context)
            .setTitle(preference.titleResId)
            .setView(layout.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                dialog.cancel()
                val updatedPassword = layout.InputText.text.toString()
                onSave(updatedPassword)
            }
            .addCancelButton()
            .setNeutralButton(R.string.clear) { dialog, _ ->
                dialog.cancel()
                onSave(PASSWORD_EMPTY)
            }
            .show()
    }
}

fun PreferenceBinding.setupAutoBackup(
    value: String,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    lastExecutionPreference: LongPreference,
    chooseBackupFolder: () -> Unit,
    onDisable: () -> Unit,
) {
    Title.setText(R.string.auto_backup)

    if (value == EMPTY_PATH) {
        Value.setText(R.string.tap_to_set_up)

        root.setOnClickListener { chooseBackupFolder() }
    } else {
        val uri = Uri.parse(value)
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        if (folder.exists()) {
            val path = uri.toReadablePath()
            Value.text = path
            lastExecutionPreference.removeObservers(lifecycleOwner)
            lastExecutionPreference.observe(lifecycleOwner) { time ->
                if (time != -1L) {
                    Value.post {
                        Value.text =
                            "${path}\n${context.getString(R.string.auto_backup_last)}: ${Date(time).toText()}"
                    }
                }
            }
        } else Value.setText(R.string.cant_find_folder)

        root.setOnClickListener {
            MenuDialog(context)
                .add(R.string.disable_auto_backup) { onDisable() }
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
    onChange: (newValue: Int) -> Unit,
) {
    Title.setText(titleResId)

    Slider.apply {
        valueTo = max.toFloat()
        valueFrom = min.toFloat()
        this@apply.value = value.toFloat()
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
