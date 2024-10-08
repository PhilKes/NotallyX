package com.philkes.notallyx.fragments

import android.os.Build
import android.os.Bundle
import android.view.View
import com.philkes.notallyx.R
import com.philkes.notallyx.model.Folder

class Search : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.ChipGroup?.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding?.RecyclerView?.scrollIndicators = View.SCROLL_INDICATOR_TOP
        }
        super.onViewCreated(view, savedInstanceState)

        val checked =
            when (model.folder) {
                Folder.NOTES -> R.id.Notes
                Folder.DELETED -> R.id.Deleted
                Folder.ARCHIVED -> R.id.Archived
            }

        binding?.ChipGroup?.check(checked)

        binding?.ChipGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.Notes -> model.folder = Folder.NOTES
                R.id.Deleted -> model.folder = Folder.DELETED
                R.id.Archived -> model.folder = Folder.ARCHIVED
            }
        }
    }

    override fun getBackground() = R.drawable.search

    override fun getObservable() = model.searchResults
}
