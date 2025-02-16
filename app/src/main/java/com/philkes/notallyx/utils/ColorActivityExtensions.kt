package com.philkes.notallyx.utils

import android.app.Activity
import android.view.View
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Color
import com.philkes.notallyx.data.model.ColorString
import com.philkes.notallyx.databinding.DialogColorBinding
import com.philkes.notallyx.databinding.DialogColorPickerBinding
import com.philkes.notallyx.presentation.createTextView
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.extractColor
import com.philkes.notallyx.presentation.setLightStatusAndNavBar
import com.philkes.notallyx.presentation.showAndFocus
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
        MainListView.adapter = colorAdapter
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
    var editTextChangedByUser = false
    val binding =
        DialogColorPickerBinding.inflate(layoutInflater).apply {
            BrightnessSlideBar.setSelectorDrawableRes(
                com.skydoves.colorpickerview.R.drawable.colorpickerview_wheel
            )
            ColorPicker.apply {
                BrightnessSlideBar.attachColorPickerView(ColorPicker)
                attachBrightnessSlider(BrightnessSlideBar)
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
    MaterialAlertDialogBuilder(this).apply {
        setTitle(if (oldColor != null) R.string.edit_color else R.string.new_color)
        setView(binding.root)
        setPositiveButton(R.string.save) { _, _ ->
            val newColor = binding.ColorPicker.colorEnvelope.toColorString()
            if (newColor == oldColor) {
                callback(oldColor, null)
            } else {
                callback(newColor, oldColor)
            }
        }
        setNegativeButton(R.string.back) { _, _ ->
            showColorSelectDialog(colors, oldColor, setNavigationbarLight, callback, deleteCallback)
        }
        oldColor?.let {
            setNeutralButton(R.string.delete) { _, _ ->
                showDeleteColorDialog(
                    colors,
                    oldColor,
                    setNavigationbarLight,
                    callback,
                    deleteCallback,
                )
            }
        }
        showAndFocus(
            allowFullSize = true,
            onShowListener = {
                setNavigationbarLight?.let {
                    window?.apply { setLightStatusAndNavBar(it, binding.root) }
                }
            },
            applyToPositiveButton = { positiveButton ->
                binding.apply {
                    BrightnessSlideBar.setSelectorDrawableRes(
                        com.skydoves.colorpickerview.R.drawable.colorpickerview_wheel
                    )
                    ColorPicker.setColorListener(
                        ColorEnvelopeListener { color, _ ->
                            TileView.setPaintColor(color.color)
                            val colorString = color.toColorString()
                            val isSaveEnabled = colorString == oldColor || colorString !in colors
                            positiveButton.isEnabled = isSaveEnabled
                            ColorExistsText.visibility =
                                if (isSaveEnabled) View.INVISIBLE else View.VISIBLE
                            if (!editTextChangedByUser) {
                                ColorCode.setText(color.hexCode.argbToRgbString())
                            } else editTextChangedByUser = false
                        }
                    )
                }
            },
        )
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
            .setCustomTitle(createTextView(R.string.delete_color_message))
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
        MainListView.apply {
            updatePadding(left = 2.dp, right = 2.dp)
            (layoutManager as? GridLayoutManager)?.let { it.spanCount = 6 }
            adapter = colorAdapter
        }
        Message.isVisible = false
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
