package com.philkes.notallyx.presentation.view.note.action

import android.os.Build
import com.philkes.notallyx.R

/** BottomSheet inside note for adding files, recording audio. */
class AddBottomSheet(callbacks: AddActions) : ActionBottomSheet(createActions(callbacks)) {

    companion object {
        const val TAG = "AddBottomSheet"

        fun createActions(callbacks: AddActions) =
            listOf(
                Action(R.string.add_images, R.drawable.add_images) { callbacks.addImages() },
                Action(R.string.attach_file, R.drawable.text_file) { callbacks.attachFiles() },
            ) +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    listOf(
                        Action(R.string.record_audio, R.drawable.record_audio) {
                            callbacks.recordAudio()
                        }
                    )
                else listOf()
    }
}

interface AddActions {
    fun addImages()

    fun attachFiles()

    fun recordAudio()
}
