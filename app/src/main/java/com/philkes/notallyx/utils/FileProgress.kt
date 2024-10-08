package com.philkes.notallyx.utils

import com.philkes.notallyx.presentation.viewmodel.NotallyModel

class FileProgress(
    val inProgress: Boolean,
    val current: Int,
    val total: Int,
    val fileType: NotallyModel.FileType,
)
