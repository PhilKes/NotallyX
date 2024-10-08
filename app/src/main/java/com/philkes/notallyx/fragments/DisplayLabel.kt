package com.philkes.notallyx.fragments

import androidx.lifecycle.LiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.miscellaneous.Constants
import com.philkes.notallyx.model.Item

class DisplayLabel : NotallyFragment() {

    override fun getBackground() = R.drawable.label

    override fun getObservable(): LiveData<List<Item>> {
        val label = requireNotNull(requireArguments().getString(Constants.SelectedLabel))
        return model.getNotesByLabel(label)
    }
}
