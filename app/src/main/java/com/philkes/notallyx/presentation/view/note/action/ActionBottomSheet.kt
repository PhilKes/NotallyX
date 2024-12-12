package com.philkes.notallyx.presentation.view.note.action

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.philkes.notallyx.databinding.BottomSheetActionBinding
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.getColorFromAttr

/** Helper to create [BottomSheetDialogFragment] to display actions. */
open class ActionBottomSheet(private val actions: Collection<Action>) :
    BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view =
            LinearLayout(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    )
                orientation = LinearLayout.VERTICAL
                setPadding(8.dp(context), 18.dp(context), 8.dp(context), 8.dp(context))
            }

        actions.forEach { action ->
            if (action.showDividerAbove) {
                val divider =
                    View(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                .apply { setMargins(8.dp(context), 0, 8.dp(context), 0) }
                        setBackgroundColor(
                            context.getColorFromAttr(
                                com.google.android.material.R.attr.colorOnSurfaceVariant
                            )
                        )
                    }
                view.addView(divider)
            }
            val textView =
                BottomSheetActionBinding.inflate(inflater, view, false).root.apply {
                    text = getString(action.labelResId)
                    setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(context, action.drawableResId),
                        null,
                        null,
                        null,
                    )
                    setOnClickListener {
                        action.callback()
                        hide()
                    }
                }
            view.addView(textView)
        }

        return view
    }

    private fun BottomSheetDialogFragment.hide() {
        (dialog as? BottomSheetDialog)?.behavior?.state = STATE_HIDDEN
    }
}

data class Action(
    val labelResId: Int,
    val drawableResId: Int,
    val showDividerAbove: Boolean = false,
    val callback: () -> Unit,
)
