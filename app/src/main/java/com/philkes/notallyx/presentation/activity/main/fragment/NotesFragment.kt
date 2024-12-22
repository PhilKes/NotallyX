package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder

class NotesFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getObservable() = model.baseNotes!!

    override fun getBackground() = R.drawable.notebook
}
