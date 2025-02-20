package com.philkes.notallyx.presentation.view.note.listitem

import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.philkes.notallyx.data.model.ListItem

class ListItemList(private val adapter: ListItemAdapter) : ArrayList<ListItem>() {

    fun setChecked(position: Int, checked: Boolean, checkChildren: Boolean) {
        val item = this[position]
        if (item.checked != checked) {
            item.checked = checked
        }
        if (checkChildren) {
            item.children.forEach { it.checked = checked }
        }
        adapter.notifyItemRangeChanged(position, 1 + item.children.size)
    }
}
