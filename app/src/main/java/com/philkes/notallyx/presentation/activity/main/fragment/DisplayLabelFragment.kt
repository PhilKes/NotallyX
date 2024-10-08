package com.philkes.notallyx.presentation.activity.main.fragment

import androidx.lifecycle.LiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.presentation.view.Constants

class DisplayLabelFragment : NotallyFragment() {

    override fun getBackground() = R.drawable.label

    override fun getObservable(): LiveData<List<Item>> {
        val label = requireNotNull(requireArguments().getString(Constants.SelectedLabel))
        return model.getNotesByLabel(label)
    }
}
