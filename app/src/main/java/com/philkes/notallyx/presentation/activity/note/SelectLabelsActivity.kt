package com.philkes.notallyx.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.databinding.ActivityLabelBinding
import com.philkes.notallyx.databinding.DialogInputBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.showAndFocus
import com.philkes.notallyx.presentation.showToast
import com.philkes.notallyx.presentation.view.main.label.SelectableLabelAdapter
import com.philkes.notallyx.presentation.viewmodel.LabelModel

class SelectLabelsActivity : LockedActivity<ActivityLabelBinding>() {

    private val model: LabelModel by viewModels()

    private lateinit var selectedLabels: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedList = savedInstanceState?.getStringArrayList(EXTRA_SELECTED_LABELS)
        val passedList = requireNotNull(intent.getStringArrayListExtra(EXTRA_SELECTED_LABELS))
        selectedLabels = savedList ?: passedList

        val result = Intent()
        result.putExtra(EXTRA_SELECTED_LABELS, selectedLabels)
        setResult(RESULT_OK, result)

        setupToolbar()
        setupRecyclerView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(EXTRA_SELECTED_LABELS, selectedLabels)
    }

    private fun setupToolbar() {
        binding.Toolbar.apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.add_label, R.drawable.add) { addLabel() }
        }
    }

    private fun addLabel() {
        val binding = DialogInputBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_label)
            .setView(binding.root)
            .setCancelButton()
            .setPositiveButton(R.string.save) { dialog, _ ->
                val value = binding.EditText.text.toString().trim()
                if (value.isNotEmpty()) {
                    val label = Label(value)
                    baseModel.insertLabel(label) { success ->
                        if (success) {
                            dialog.dismiss()
                        } else showToast(R.string.label_exists)
                    }
                }
            }
            .showAndFocus(binding.EditText)
    }

    private fun setupRecyclerView() {
        val labelAdapter = SelectableLabelAdapter(selectedLabels)
        labelAdapter.onChecked = { position, checked ->
            if (position != -1) {
                val label = labelAdapter.currentList[position]
                if (checked) {
                    if (!selectedLabels.contains(label)) {
                        selectedLabels.add(label)
                    }
                } else selectedLabels.remove(label)
            }
        }

        binding.RecyclerView.apply {
            setHasFixedSize(true)
            adapter = labelAdapter
            addItemDecoration(
                DividerItemDecoration(this@SelectLabelsActivity, RecyclerView.VERTICAL)
            )
        }

        baseModel.labels.observe(this) { labels ->
            labelAdapter.submitList(labels)
            if (labels.isEmpty()) {
                binding.EmptyState.visibility = View.VISIBLE
            } else binding.EmptyState.visibility = View.INVISIBLE
        }
    }

    companion object {
        const val EXTRA_SELECTED_LABELS = "notallyx.intent.extra.SELECTED_LABELS"
    }
}
