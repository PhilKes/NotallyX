package com.omgodse.notally.miscellaneous

import com.omgodse.notally.room.ListItem

interface ListItemSorterStrategy {

    fun sort(
        list: MutableList<ListItem>,
        initSortingPosition: Boolean = false,
    ): MutableList<ListItem>
}
