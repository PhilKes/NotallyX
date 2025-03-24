package com.philkes.notallyx.presentation.view.note.action

import androidx.annotation.ColorInt
import com.philkes.notallyx.R
import com.philkes.notallyx.databinding.BottomSheetActionBinding
import com.philkes.notallyx.presentation.viewmodel.ExportMimeType

/** BottomSheet inside list-note for all common note actions. */
class MoreNoteBottomSheet(
    callbacks: MoreActions,
    additionalActions: Collection<Action> = listOf(),
    @ColorInt color: Int?,
) : ActionBottomSheet(createActions(callbacks, additionalActions), color) {

    companion object {
        const val TAG = "MoreNoteBottomSheet"

        internal fun createActions(callbacks: MoreActions, additionalActions: Collection<Action>) =
            listOf(
                Action(R.string.share, R.drawable.share) { _ ->
                    callbacks.share()
                    true
                },
                Action(R.string.export, R.drawable.export) { fragment ->
                    fragment.layout.removeAllViews()
                    ExportMimeType.entries.forEach { mimeType ->
                        BottomSheetActionBinding.inflate(fragment.inflater, fragment.layout, true)
                            .root
                            .apply {
                                text = mimeType.name
                                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                                setOnClickListener {
                                    callbacks.export(mimeType)
                                    fragment.dismiss()
                                }
                            }
                    }
                    false
                },
                Action(R.string.change_color, R.drawable.change_color) { _ ->
                    callbacks.changeColor()
                    true
                },
                Action(R.string.reminders, R.drawable.notifications) { _ ->
                    callbacks.changeReminders()
                    true
                },
                Action(R.string.labels, R.drawable.label) { _ ->
                    callbacks.changeLabels()
                    true
                },
            ) + additionalActions
    }
}

interface MoreActions {
    fun share()

    fun export(mimeType: ExportMimeType)

    fun changeColor()

    fun changeReminders()

    fun changeLabels()
}
