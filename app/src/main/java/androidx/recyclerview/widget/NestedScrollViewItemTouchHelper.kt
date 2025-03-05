package androidx.recyclerview.widget

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

class NestedScrollViewItemTouchHelper(
    callback: Callback,
    private val scrollView: NestedScrollView,
) : ItemTouchHelper(callback) {
    private var selectedStartY: Int = -1
    private var selectedStartScrollY: Float = -1f
    private var selectedView: View? = null
    private var dragScrollStartTimeInMs: Long = 0

    private var lastmDy = 0f
    private var lastScrollY = 0
    private var tmpRect: Rect? = null

    override fun select(selected: RecyclerView.ViewHolder?, actionState: Int) {
        super.select(selected, actionState)
        if (selected != null) {
            selectedView = selected.itemView
            selectedStartY = selected.itemView.top
            selectedStartScrollY = scrollView!!.scrollY.toFloat()
        }
    }

    /**
     * Scrolls [scrollView] when an item in [mRecyclerView] is dragged to the top or bottom of the
     * [scrollView].
     *
     * Inspired by
     * [https://stackoverflow.com/a/70699988/9748566](https://stackoverflow.com/a/70699988/9748566)
     */
    override fun scrollIfNecessary(): Boolean {
        if (mSelected == null) {
            dragScrollStartTimeInMs = Long.MIN_VALUE
            return false
        }
        val now = System.currentTimeMillis()
        val scrollDuration =
            if (dragScrollStartTimeInMs == Long.MIN_VALUE) 0 else now - dragScrollStartTimeInMs
        val lm = mRecyclerView.layoutManager
        if (tmpRect == null) {
            tmpRect = Rect()
        }
        var scrollY = 0
        val currentScrollY = scrollView.scrollY

        // We need to use the height of NestedScrollView, not RecyclerView's!
        val actualShowingHeight =
            scrollView.height - mRecyclerView.top - mRecyclerView.paddingBottom

        lm!!.calculateItemDecorationsForChild(mSelected.itemView, tmpRect!!)
        if (lm.canScrollVertically()) {
            // Keep scrolling if the user didnt change the drag direction
            if (lastScrollY != 0 && abs(lastmDy) >= abs(mDy)) {
                scrollY = lastScrollY
            } else {
                // The true current Y of the item in NestedScrollView, not in RecyclerView!
                val curY = (selectedStartY + mDy - currentScrollY).toInt()
                // The true mDy should plus the initial scrollY and minus current scrollY of
                // NestedScrollView
                val checkDy = (mDy + selectedStartScrollY - currentScrollY).toInt()
                val topDiff = curY - tmpRect!!.top - mRecyclerView.paddingTop
                if (checkDy < 0 && topDiff < 0) { // User is draging the item out of the top edge.
                    scrollY = topDiff
                } else if (checkDy > 0) { // User is draging the item out of the bottom edge.
                    val bottomDiff = (curY + mSelected.itemView.height - actualShowingHeight) + 10
                    if (bottomDiff >= 0) {
                        scrollY = bottomDiff
                    }
                } else {
                    scrollY = 0
                }
            }
        }
        lastScrollY = scrollY
        lastmDy = mDy
        if (scrollY != 0) {
            scrollY =
                mCallback.interpolateOutOfBoundsScroll(
                    mRecyclerView,
                    mSelected.itemView.height,
                    scrollY,
                    actualShowingHeight,
                    scrollDuration,
                )
        }
        if (scrollY != 0) {
            val maxScrollY = scrollView.childrenHeightsSum - scrollView.height
            // Check if we can scroll further before applying the scroll
            if (
                (scrollY < 0 && scrollView.scrollY > 0) ||
                    (scrollY > 0 && scrollView.scrollY < maxScrollY)
            ) {
                if (dragScrollStartTimeInMs == Long.MIN_VALUE) {
                    dragScrollStartTimeInMs = now
                }
                scrollView.scrollBy(0, scrollY)
                // Update the dragged item position as well
                selectedView?.translationY = selectedView!!.translationY + scrollY
                return true
            }
        }
        dragScrollStartTimeInMs = Long.MIN_VALUE
        lastScrollY = 0
        lastmDy = 0f
        return false
    }

    private val ViewGroup.childrenHeightsSum
        get() = children.map { it.measuredHeight }.sum()
}
