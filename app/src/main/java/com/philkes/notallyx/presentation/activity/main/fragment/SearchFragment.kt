package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder

class SearchFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val initialFolder =
            arguments?.let {
                BundleCompat.getSerializable(it, EXTRA_INITIAL_FOLDER, Folder::class.java)
            }
        binding?.ChipGroup?.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding?.RecyclerView?.scrollIndicators = View.SCROLL_INDICATOR_TOP
        }
        super.onViewCreated(view, savedInstanceState)

        val checked =
            when (initialFolder ?: model.folder.value) {
                Folder.NOTES -> R.id.Notes
                Folder.DELETED -> R.id.Deleted
                Folder.ARCHIVED -> R.id.Archived
            }

        binding?.ChipGroup?.apply {
            setOnCheckedStateChangeListener { _, checkedId ->
                when (checkedId.first()) {
                    R.id.Notes -> model.folder.value = Folder.NOTES
                    R.id.Deleted -> model.folder.value = Folder.DELETED
                    R.id.Archived -> model.folder.value = Folder.ARCHIVED
                }
            }
            check(checked)
        }
    }

    override fun getBackground() = R.drawable.search

    override fun getObservable() = model.searchResults!!

    companion object {
        const val EXTRA_INITIAL_FOLDER = "notallyx.intent.extra.INITIAL_FOLDER"
    }
}
