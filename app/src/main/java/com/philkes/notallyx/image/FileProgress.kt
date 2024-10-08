package com.philkes.notallyx.image

import com.philkes.notallyx.viewmodels.NotallyModel

class FileProgress(
    val inProgress: Boolean,
    val current: Int,
    val total: Int,
    val fileType: NotallyModel.FileType,
)
