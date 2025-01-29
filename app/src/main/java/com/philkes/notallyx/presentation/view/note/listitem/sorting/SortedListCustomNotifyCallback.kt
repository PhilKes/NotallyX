package com.philkes.notallyx.presentation.view.note.listitem.sorting

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedListAdapterCallback

abstract class SortedListCustomNotifyCallback<T>(adapter: RecyclerView.Adapter<*>?) :
    SortedListAdapterCallback<T>(adapter) {

    private var isNotifyEnabled = true

    fun setNotifyEnabled(value: Boolean) {
        isNotifyEnabled = value
    }

    override fun onChanged(position: Int, count: Int) {
        if (isNotifyEnabled) {
            super.onChanged(position, count)
        }
    }

    override fun onInserted(position: Int, count: Int) {
        if (isNotifyEnabled) {
            super.onInserted(position, count)
        }
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        if (isNotifyEnabled) {
            super.onMoved(fromPosition, toPosition)
        }
    }

    override fun onRemoved(position: Int, count: Int) {
        if (isNotifyEnabled) {
            super.onRemoved(position, count)
        }
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        if (isNotifyEnabled) {
            super.onChanged(position, count, payload)
        }
    }
}
