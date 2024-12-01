package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.presentation.view.Constants

class DisplayLabelFragment : NotallyFragment() {

    private lateinit var label: String

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getBackground() = R.drawable.label

    override fun getObservable(): LiveData<List<Item>> {
        label = requireNotNull(requireArguments().getString(Constants.SelectedLabel))
        return model.getNotesByLabel(label)
    }

    override fun prepareNewNoteIntent(intent: Intent): Intent {
        return intent.putExtra(Constants.SelectedLabel, label)
    }
}
