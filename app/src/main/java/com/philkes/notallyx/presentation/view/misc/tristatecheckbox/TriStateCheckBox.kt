package com.philkes.notallyx.presentation.view.misc.tristatecheckbox

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.dp

/**
 * [AppCompatCheckBox] with 3 states ([State.UNCHECKED],[State.CHECKED],[State.PARTIALLY_CHECKED])
 */
class TriStateCheckBox
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    AppCompatCheckBox(context, attrs, defStyleAttr) {

    enum class State {
        UNCHECKED,
        CHECKED,
        PARTIALLY_CHECKED,
    }

    var state: State = State.UNCHECKED
        private set

    fun setState(state: State, animate: Boolean = true) {
        if (this.state != state) {
            this.state = state
            if (animate) {
                animateButtonDrawable()
            } else {
                buttonDrawable = getCurrentDrawable()
            }
        }
    }

    private val uncheckedDrawable: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.check_box_unchecked)
    private val checkedDrawable: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.check_box_checked)
    private val partiallyCheckedDrawable: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.check_box_partial)

    init {
        compoundDrawablePadding = 4.dp(context)
        buttonDrawable = getCurrentDrawable()
        setOnClickListener { toggleState() }
    }

    fun toggleState() {
        setState(
            when (state) {
                State.UNCHECKED -> State.CHECKED
                State.PARTIALLY_CHECKED -> State.UNCHECKED
                State.CHECKED -> State.UNCHECKED
            }
        )
    }

    private fun animateButtonDrawable() {
        val targetDrawable = getCurrentDrawable()
        val currentDrawable =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) buttonDrawable else background
        val fadeOut =
            ObjectAnimator.ofInt(currentDrawable, "alpha", 255, 0).apply {
                duration = 33
                addListener(onEnd = { buttonDrawable = targetDrawable })
            }

        val fadeIn =
            ObjectAnimator.ofInt(currentDrawable, "alpha", 0, 255).apply {
                duration = 166
                interpolator =
                    AnimationUtils.loadInterpolator(
                        context,
                        androidx.appcompat.R.interpolator
                            .btn_checkbox_unchecked_mtrl_animation_interpolator_0,
                    )
            }
        AnimatorSet().apply { playSequentially(fadeOut, fadeIn) }.start()
    }

    private fun getCurrentDrawable() =
        when (state) {
            State.UNCHECKED -> uncheckedDrawable
            State.CHECKED -> checkedDrawable
            State.PARTIALLY_CHECKED -> partiallyCheckedDrawable
        }
}
