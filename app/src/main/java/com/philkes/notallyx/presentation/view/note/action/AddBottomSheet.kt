package com.philkes.notallyx.presentation.view.note.action

import android.os.Build
import androidx.annotation.ColorInt
import com.philkes.notallyx.R

/** BottomSheet inside note for adding files, recording audio. */
class AddBottomSheet(callbacks: AddActions, @ColorInt color: Int?) :
    ActionBottomSheet(createActions(callbacks), color) {

    companion object {
        const val TAG = "AddBottomSheet"

        fun createActions(callbacks: AddActions) =
            listOf(
                Action(R.string.add_images, R.drawable.add_images) { _ ->
                    callbacks.addImages()
                    true
                },
                Action(R.string.attach_file, R.drawable.text_file) { _ ->
                    callbacks.attachFiles()
                    true
                },
            ) +
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    listOf(
                        Action(R.string.record_audio, R.drawable.record_audio) { _ ->
                            callbacks.recordAudio()
                            true
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
