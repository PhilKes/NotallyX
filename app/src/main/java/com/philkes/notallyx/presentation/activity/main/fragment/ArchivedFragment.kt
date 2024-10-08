package com.philkes.notallyx.presentation.activity.main.fragment

import com.philkes.notallyx.R

class ArchivedFragment : NotallyFragment() {

    override fun getBackground() = R.drawable.archive

    override fun getObservable() = model.archivedNotes
}
