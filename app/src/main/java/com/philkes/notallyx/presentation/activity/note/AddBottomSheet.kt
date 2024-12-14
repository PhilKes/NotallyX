package com.philkes.notallyx.presentation.activity.note

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.philkes.notallyx.databinding.DialogAddMoreBinding

class AddBottomSheet(
    val onAddImage: () -> Unit,
    val onAddFile: () -> Unit,
    val onRecordAudio: () -> Unit,
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = DialogAddMoreBinding.inflate(inflater, container, false)
        view.AddImage.setOnClickListener {
            onAddImage()
            hide()
        }
        view.AttachFile.setOnClickListener {
            onAddFile()
            hide()
        }
        view.RecordAudio.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setOnClickListener {
                    onRecordAudio()
                    hide()
                }
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        return view.root
    }

    private fun BottomSheetDialogFragment.hide() {
        (dialog as? BottomSheetDialog)?.behavior?.state = STATE_HIDDEN
    }

    companion object {
        const val TAG = "AddBottomSheet"
    }
}
