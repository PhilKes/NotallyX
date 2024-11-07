package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.navigation.fragment.findNavController
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.presentation.activity.main.fragment.SearchFragment.Companion.EXTRA_INITIAL_FOLDER
import com.philkes.notallyx.presentation.add

class NotesFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(R.string.search, R.drawable.search) {
            val bundle = Bundle().apply { putSerializable(EXTRA_INITIAL_FOLDER, Folder.NOTES) }
            findNavController().navigate(R.id.NotesToSearch, bundle)
        }
    }

    override fun getObservable() = model.baseNotes!!

    override fun getBackground() = R.drawable.notebook
}
