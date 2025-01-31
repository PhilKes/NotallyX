package com.philkes.notallyx.utils

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.ColorString
import com.philkes.notallyx.databinding.DialogColorBinding
import com.philkes.notallyx.databinding.DialogColorPickerBinding
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.setLightStatusAndNavBar
import com.philkes.notallyx.presentation.view.main.ColorAdapter
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

fun Activity.showColorSelectDialog(
    colors: List<String>,
    currentColor: String?,
    setNavigationbarLight: Boolean?,
    callback: (selectedColor: String, oldColor: String?) -> Unit,
    deleteCallback: (colorToDelete: String, newColor: String) -> Unit,
) {
    val actualColors =
        colors.toMutableList().apply {
            remove(BaseNote.COLOR_DEFAULT)
            remove(BaseNote.COLOR_NEW)
            add(0, BaseNote.COLOR_DEFAULT)
            add(0, BaseNote.COLOR_NEW)
        }
    val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.change_color).create()
    val colorAdapter =
        ColorAdapter(
            actualColors,
            currentColor,
            object : ItemListener {
                override fun onClick(position: Int) {
                    dialog.dismiss()
                    val selectedColor = actualColors[position]
                    if (selectedColor == BaseNote.COLOR_NEW) {
                        showEditColorDialog(
                            actualColors,
                            null,
                            setNavigationbarLight,
                            callback,
                            deleteCallback,
                        )
                    } else callback(selectedColor, null)
                }

                override fun onLongClick(position: Int) {
                    val oldColor = actualColors[position]
                    if (oldColor == BaseNote.COLOR_DEFAULT || oldColor == BaseNote.COLOR_NEW) {
                        return
                    }
                    dialog.dismiss()
                    showEditColorDialog(
                        actualColors,
                        oldColor,
                        setNavigationbarLight,
                        callback,
                        deleteCallback,
                    )
                }
            },
        )
    DialogColorBinding.inflate(layoutInflater).apply {
        RecyclerView.adapter = colorAdapter
        dialog.setView(root)
        dialog.setOnShowListener {
            setNavigationbarLight?.let { dialog.window?.setLightStatusAndNavBar(it) }
        }
        dialog.show()
    }
}

private fun Activity.showEditColorDialog(
    colors: List<String>,
    oldColor: String?,
    setNavigationbarLight: Boolean?,
    callback: (selectedColor: String, oldColor: String?) -> Unit,
    deleteCallback: (colorToDelete: String, newColor: String) -> Unit,
) {
    val selectedColor = oldColor?.let { extractColor(it) } ?: extractColor(BaseNote.COLOR_DEFAULT)
    val binding = DialogColorPickerBinding.inflate(layoutInflater)
    val dialogBuilder =
        MaterialAlertDialogBuilder(this)
            .setTitle(if (oldColor != null) R.string.edit_color else R.string.new_color)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newColor = binding.ColorPicker.colorEnvelope.toColorString()
                if (newColor == oldColor) {
                    callback(oldColor, null)
                } else {
                    callback(newColor, oldColor)
                }
            }
            .setNegativeButton(R.string.back) { _, _ ->
                showColorSelectDialog(
                    colors,
                    oldColor,
                    setNavigationbarLight,
                    callback,
                    deleteCallback,
                )
            }

    oldColor?.let {
        dialogBuilder.setNeutralButton(R.string.delete) { _, _ ->
            showDeleteColorDialog(colors, oldColor, setNavigationbarLight, callback, deleteCallback)
        }
    }
    val dialog = dialogBuilder.create()
    dialog.setOnShowListener {
        setNavigationbarLight?.let { window?.apply { setLightStatusAndNavBar(it, binding.root) } }
    }
    dialog.show()
    val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    var editTextChangedByUser = false
    binding.apply {
        BrightnessSlideBar.setSelectorDrawableRes(
            com.skydoves.colorpickerview.R.drawable.colorpickerview_wheel
        )
        ColorPicker.apply {
            BrightnessSlideBar.attachColorPickerView(ColorPicker)
            attachBrightnessSlider(BrightnessSlideBar)
            setColorListener(
                ColorEnvelopeListener { color, fromUser ->
                    TileView.setPaintColor(color.color)
                    val colorString = color.toColorString()
                    val isSaveEnabled = colorString == oldColor || colorString !in colors
                    positiveButton.isEnabled = isSaveEnabled
                    ColorExistsText.visibility = if (isSaveEnabled) View.INVISIBLE else View.VISIBLE
                    if (!editTextChangedByUser) {
                        ColorCode.setText(color.hexCode.argbToRgbString())
                    } else editTextChangedByUser = false
                }
            )
            setInitialColor(selectedColor)
            ColorPicker.postDelayed({ ColorPicker.selectByHsvColor(selectedColor) }, 100)
            ColorCode.doAfterTextChanged { text ->
                val isValueChangedByUser = ColorCode.hasFocus()
                val hexCode = text.toString()
                if (isValueChangedByUser && hexCode.length == 6) {
                    try {
                        val color = this@showEditColorDialog.extractColor("#$hexCode")
                        editTextChangedByUser = true
                        ColorPicker.selectByHsvColor(color)
                    } catch (e: Exception) {}
                }
            }
            CopyCode.setOnClickListener { _ ->
                this@showEditColorDialog.copyToClipBoard(ColorCode.text)
            }
        }
        Restore.setOnClickListener { ColorPicker.selectByHsvColor(selectedColor) }

        ExistingColors.apply {
            val existingColors = Color.allColorStrings()
            val colorAdapter =
                ColorAdapter(
                    existingColors,
                    null,
                    object : ItemListener {
                        override fun onClick(position: Int) {
                            ColorPicker.selectByHsvColor(
                                this@showEditColorDialog.extractColor(existingColors[position])
                            )
                        }

                        override fun onLongClick(position: Int) {}
                    },
                )
            adapter = colorAdapter
        }
    }
}

private fun Activity.showDeleteColorDialog(
    colors: List<String>,
    oldColor: String,
    setNavigationbarLight: Boolean?,
    callback: (selectedColor: String, oldColor: String?) -> Unit,
    deleteCallback: (colorToDelete: String, newColor: String) -> Unit,
) {
    val dialog =
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_color_message)
            .setNeutralButton(R.string.back) { _, _ ->
                showEditColorDialog(
                    colors,
                    oldColor,
                    setNavigationbarLight,
                    callback,
                    deleteCallback,
                )
            }
            .create()
    val selectableColors = colors.filter { it != BaseNote.COLOR_NEW && it != oldColor }
    val colorAdapter =
        ColorAdapter(
            selectableColors,
            null,
            object : ItemListener {
                override fun onClick(position: Int) {
                    dialog.dismiss()
                    val selectedColor = selectableColors[position]
                    deleteCallback(oldColor, selectedColor)
                }

                override fun onLongClick(position: Int) {}
            },
        )
    DialogColorBinding.inflate(layoutInflater).apply {
        RecyclerView.apply {
            (layoutManager as? GridLayoutManager)?.let { it.spanCount = 6 }
            adapter = colorAdapter
        }
        dialog.setView(root)
        dialog.setOnShowListener {
            setNavigationbarLight?.let { window?.apply { setLightStatusAndNavBar(it, root) } }
        }
        dialog.show()
    }
}

private fun ColorEnvelope.toColorString(): ColorString {
    return "#${hexCode.argbToRgbString()}"
}

private fun ColorString.argbToRgbString(): ColorString = substring(2)
