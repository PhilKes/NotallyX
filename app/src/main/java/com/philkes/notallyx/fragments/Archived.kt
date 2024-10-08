package com.philkes.notallyx.fragments

import com.philkes.notallyx.R

class Archived : NotallyFragment() {

    override fun getBackground() = R.drawable.archive

    override fun getObservable() = model.archivedNotes
}
