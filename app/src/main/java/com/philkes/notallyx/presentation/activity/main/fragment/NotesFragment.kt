package com.philkes.notallyx.presentation.activity.main.fragment

import android.view.Menu
import android.view.MenuInflater
import androidx.navigation.fragment.findNavController
import com.philkes.notallyx.R
import com.philkes.notallyx.utils.add

class NotesFragment : NotallyFragment() {

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(R.string.search, R.drawable.search) {
            findNavController().navigate(R.id.NotesToSearch)
        }
    }

    override fun getObservable() = model.baseNotes!!

    override fun getBackground() = R.drawable.notebook
}
