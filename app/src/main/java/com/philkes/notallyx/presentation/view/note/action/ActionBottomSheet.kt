package com.philkes.notallyx.presentation.view.note.action

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.philkes.notallyx.databinding.BottomSheetActionBinding
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.getColorFromAttr
import com.philkes.notallyx.presentation.isLightColor
import com.philkes.notallyx.presentation.setControlsContrastColorForAllViews
import com.philkes.notallyx.presentation.setLightStatusAndNavBar

/** Helper to create [BottomSheetDialogFragment] to display actions. */
open class ActionBottomSheet(
    private val actions: Collection<Action>,
    @ColorInt private val color: Int? = null,
) : BottomSheetDialogFragment() {

    lateinit var layout: LinearLayout
    lateinit var inflater: LayoutInflater

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        this.inflater = inflater
        val scrollView =
            NestedScrollView(requireContext()).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                isFillViewport = true
            }
        layout =
            LinearLayout(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                orientation = LinearLayout.VERTICAL
                setPadding(8.dp, 18.dp, 8.dp, 8.dp)
            }
        scrollView.addView(layout)
        actions.forEach { action ->
            if (action.showDividerAbove) {
                val divider =
                    View(context).apply {
                        layoutParams =
                            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                                .apply { setMargins(8.dp, 0, 8.dp, 8.dp) }
                        setBackgroundColor(
                            context.getColorFromAttr(
                                com.google.android.material.R.attr.colorOnSurfaceVariant
                            )
                        )
                    }
                layout.addView(divider)
            }
            val textView =
                BottomSheetActionBinding.inflate(inflater, layout, false).root.apply {
                    text = getString(action.labelResId)
                    setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(context, action.drawableResId),
                        null,
                        null,
                        null,
                    )
                    setOnClickListener {
                        if (action.callback(this@ActionBottomSheet)) {
                            dismiss()
                        }
                    }
                }
            layout.addView(textView)
        }

        color?.let {
            layout.apply {
                setBackgroundColor(it)
                setControlsContrastColorForAllViews(it, overwriteBackground = false)
            }
        }
        return scrollView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog =
            BottomSheetDialog(
                requireContext(),
                com.philkes.notallyx.R.style.ThemeOverlay_App_BottomSheetDialog,
            )
        color?.let {
            dialog.window?.apply {
                navigationBarColor = it
                setLightStatusAndNavBar(it.isLightColor())
            }
        }
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
                bottomSheet ->
                BottomSheetBehavior.from(bottomSheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    isHideable = false
                    // Disable dragging changes to allow nested scroll
                    setBottomSheetCallback(
                        object : BottomSheetCallback() {
                            override fun onStateChanged(bottomSheet: View, newState: Int) {
                                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                    dismiss()
                                }
                            }

                            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                                state = BottomSheetBehavior.STATE_EXPANDED
                            }
                        }
                    )
                }
            }
        }
        return dialog
    }

    fun hide() {
        (dialog as? BottomSheetDialog)?.behavior?.state = STATE_HIDDEN
    }
}

data class Action(
    val labelResId: Int,
    val drawableResId: Int,
    val showDividerAbove: Boolean = false,
    /**
     * On click callback.
     *
     * @returns whether or not the BottomSheet should be hidden
     */
    val callback: (actionBottomSheet: ActionBottomSheet) -> Boolean,
)
