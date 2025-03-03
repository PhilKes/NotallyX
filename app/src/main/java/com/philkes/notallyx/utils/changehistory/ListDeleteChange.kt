package com.philkes.notallyx.utils.changehistory

import com.philkes.notallyx.presentation.view.note.listitem.ListManager
import com.philkes.notallyx.presentation.view.note.listitem.ListState

class ListDeleteChange(old: ListState, new: ListState, listManager: ListManager) :
    ListBatchChange(old, new, listManager)
