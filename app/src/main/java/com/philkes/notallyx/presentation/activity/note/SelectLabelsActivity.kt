package com.philkes.notallyx.presentation.activity.note

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

class SelectLabelsActivity : LockedActivity<ActivityLabelBinding>() {

    private lateinit var selectedLabels: ArrayList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLabelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        val savedList = savedInstanceState?.getStringArrayList(EXTRA_SELECTED_LABELS)
        val passedList = requireNotNull(intent.getStringArrayListExtra(EXTRA_SELECTED_LABELS))
        selectedLabels = savedList ?: passedList

        val result = Intent()
        result.putExtra(EXTRA_SELECTED_LABELS, selectedLabels)
        setResult(RESULT_OK, result)

        setupToolbar()
        setupRecyclerView()
    }

    private fun configureEdgeToEdgeInsets() {
        // 1. Enable edge-to-edge display for the activity window.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 3. Apply window insets to specific views to prevent content from being obscured.
        // Set an OnApplyWindowInsetsListener on the root layout.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Adjust the top margin of the Toolbar to account for the status bar.
            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout() // Request a layout pass to apply the new margin
            }
            // Apply padding to the RecyclerView and EmptyState TextView to account for the
            // navigation bar and keyboard.
            // Preserve existing padding.
            binding.MainListView.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    systemBarsInsets.bottom + imeInsets.bottom,
                )
            }
            binding.EmptyState.apply {
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
            .showAndFocus(binding.EditText, allowFullSize = true)
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

        binding.MainListView.apply {
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
