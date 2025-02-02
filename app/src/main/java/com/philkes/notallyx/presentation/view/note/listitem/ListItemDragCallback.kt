package com.philkes.notallyx.presentation.view.note.listitem

import android.graphics.Canvas
import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.philkes.notallyx.data.model.ListItem

/** ItemTouchHelper.Callback that allows dragging ListItem with its children. */
class ListItemDragCallback(private val elevation: Float, internal val listManager: ListManager) :
    ItemTouchHelper.Callback() {

    private var lastState = ItemTouchHelper.ACTION_STATE_IDLE
    private var lastIsCurrentlyActive = false
    private var childViewHolders: List<ViewHolder> = mutableListOf()

    private var itemsBefore: List<ListItem>? = null
    private var positionFrom: Int? = null
    private var positionTo: Int? = null
    private var newPosition: Int? = null

    override fun isLongPressDragEnabled() = false

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {}

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
        val drag = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(drag, 0)
    }

    override fun onMove(view: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
        val from = viewHolder.absoluteAdapterPosition
        val to = target.absoluteAdapterPosition
        if (from == -1 || to == -1) {
            return false
        }
        return move(from, to)
    }

    internal fun move(from: Int, to: Int): Boolean {
        if (positionFrom == null) {
            itemsBefore = listManager.getItems()
            listManager.startDrag(from)
        }
        val swapped =
            listManager.move(from, to, pushChange = false, updateChildren = false, isDrag = true)
        if (swapped != null) {
            if (positionFrom == null) {
                positionFrom = from
            }
            positionTo = to
            newPosition = swapped
        }
        return swapped != null
    }

    override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
        if (
            lastState != actionState &&
                actionState == ItemTouchHelper.ACTION_STATE_IDLE &&
                positionTo != -1
        ) {
            onDragEnd()
        }
        lastState = actionState
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean,
    ) {
        viewHolder.itemView.translationX = dX
        viewHolder.itemView.translationY = dY
        if (isCurrentlyActive) {
            viewHolder.itemView.elevation = elevation
        }
        if (lastIsCurrentlyActive != isCurrentlyActive && isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                onDragStart(viewHolder, recyclerView)
            }
        }
        lastIsCurrentlyActive = isCurrentlyActive
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
        viewHolder.itemView.apply {
            translationX = 0f
            translationY = 0f
            elevation = 0f
        }
        childViewHolders.forEach { animateFadeIn(it) }
    }

    internal fun onDragStart(viewHolder: ViewHolder, recyclerView: RecyclerView) {
        Log.d(TAG, "onDragStart")
        reset()
        if (viewHolder.absoluteAdapterPosition == -1) {
            return
        }
        val item = listManager.getItem(viewHolder.absoluteAdapterPosition)
        if (!item.isChild) {
            childViewHolders =
                item.children.mapIndexedNotNull { index, _ ->
                    recyclerView.findViewHolderForAdapterPosition(
                        viewHolder.absoluteAdapterPosition + index + 1
                    )
                }
            childViewHolders.forEach { animateFadeOut(it) }
        }
    }

    internal fun reset() {
        positionFrom = null
        positionTo = null
        newPosition = null
        itemsBefore = null
    }

    internal fun onDragEnd() {
        Log.d(TAG, "onDragEnd: from: $positionFrom to: $positionTo")
        if (positionFrom != null) {
            listManager.endDrag()
        }
        if (positionFrom == positionTo) {
            return
        }
        if (newPosition != null && itemsBefore != null) {
            // The items have already been moved accordingly via move() calls
            listManager.finishMove(
                positionFrom!!,
                positionTo!!,
                newPosition!!,
                itemsBefore!!,
                updateIsChild = true,
                updateChildren = true,
                pushChange = true,
            )
        }
    }

    private fun animateFadeOut(viewHolder: ViewHolder) {
        viewHolder.itemView.animate().translationY(-100f).alpha(0f).setDuration(300).start()
    }

    private fun animateFadeIn(viewHolder: ViewHolder) {
        viewHolder.itemView.animate().translationY(0f).alpha(1f).setDuration(300).start()
    }

    companion object {
        private const val TAG = "DragCallback"
    }
}
