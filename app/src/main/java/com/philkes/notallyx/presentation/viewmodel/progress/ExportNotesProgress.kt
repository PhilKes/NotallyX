package com.philkes.notallyx.presentation.viewmodel.progress

import com.philkes.notallyx.R
import com.philkes.notallyx.presentation.view.misc.Progress

open class ExportNotesProgress(
    current: Int = 0,
    total: Int = 0,
    inProgress: Boolean = true,
    indeterminate: Boolean = false,
) : Progress(R.string.export, current, total, inProgress, indeterminate)
