package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Label
import com.philkes.notallyx.databinding.DialogInputBinding
import com.philkes.notallyx.databinding.FragmentNotesBinding
import com.philkes.notallyx.presentation.view.Constants
import com.philkes.notallyx.presentation.view.main.LabelAdapter
import com.philkes.notallyx.presentation.view.misc.MenuDialog
import com.philkes.notallyx.presentation.view.note.listitem.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.utils.add

class LabelsFragment : Fragment(), ItemListener {

    private var labelAdapter: LabelAdapter? = null
    private var binding: FragmentNotesBinding? = null

    private val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        labelAdapter = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        labelAdapter = LabelAdapter(this)

        binding?.RecyclerView?.apply {
            setHasFixedSize(true)
            adapter = labelAdapter
            layoutManager = LinearLayoutManager(requireContext())
            val itemDecoration = DividerItemDecoration(requireContext(), RecyclerView.VERTICAL)
            addItemDecoration(itemDecoration)
            setPadding(0, 0, 0, 0)
            binding?.ImageView?.setImageResource(R.drawable.label)
        }

        setupObserver()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        setHasOptionsMenu(true)
        binding = FragmentNotesBinding.inflate(inflater)
        return binding?.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(R.string.add_label, R.drawable.add) { displayAddLabelDialog() }
    }

    override fun onClick(position: Int) {
        labelAdapter?.currentList?.get(position)?.let { value ->
            val bundle = Bundle()
            bundle.putString(Constants.SelectedLabel, value)
            findNavController().navigate(R.id.LabelsToDisplayLabel, bundle)
        }
    }

    override fun onLongClick(position: Int) {
        labelAdapter?.currentList?.get(position)?.let { value ->
            MenuDialog(requireContext())
                .add(R.string.edit) { displayEditLabelDialog(value) }
                .add(R.string.delete) { confirmDeletion(value) }
                .show()
        }
    }

    private fun setupObserver() {
        model.labels.observe(viewLifecycleOwner) { labels ->
            labelAdapter?.submitList(labels)
            binding?.ImageView?.isVisible = labels.isEmpty()
        }
    }

    private fun displayAddLabelDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogBinding = DialogInputBinding.inflate(inflater)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_label)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val value = dialogBinding.EditText.text.toString().trim()
                if (value.isNotEmpty()) {
                    val label = Label(value)
                    model.insertLabel(label) { success: Boolean ->
                        if (success) {
                            dialog.dismiss()
                        } else
                            Toast.makeText(context, R.string.label_exists, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()

        dialogBinding.EditText.requestFocus()
    }

    private fun confirmDeletion(value: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_label)
            .setMessage(R.string.your_notes_associated)
            .setPositiveButton(R.string.delete) { _, _ -> model.deleteLabel(value) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun displayEditLabelDialog(oldValue: String) {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)

        dialogBinding.EditText.setText(oldValue)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setTitle(R.string.edit_label)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val value = dialogBinding.EditText.text.toString().trim()
                if (value.isNotEmpty()) {
                    model.updateLabel(oldValue, value) { success ->
                        if (success) {
                            dialog.dismiss()
                        } else
                            Toast.makeText(
                                    requireContext(),
                                    R.string.label_exists,
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                    }
                }
            }
            .show()

        dialogBinding.EditText.requestFocus()
    }
}
