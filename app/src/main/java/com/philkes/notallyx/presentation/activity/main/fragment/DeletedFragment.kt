package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_FOLDER
import com.philkes.notallyx.presentation.add

class DeletedFragment : NotallyFragment() {

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(R.string.delete_all, R.drawable.delete_all) { deleteAllNotes() }
        menu.add(R.string.search, R.drawable.search) {
            val bundle = Bundle().apply { putSerializable(EXTRA_INITIAL_FOLDER, Folder.DELETED) }
            findNavController().navigate(R.id.DeletedToSearch, bundle)
        }
    }

    private fun deleteAllNotes() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.delete_all_notes)
            .setPositiveButton(R.string.delete) { _, _ -> model.deleteAllTrashedBaseNotes() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun getBackground() = R.drawable.delete

    override fun getObservable() = model.deletedNotes!!
}
