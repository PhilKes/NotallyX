package com.omgodse.notally.image

import com.omgodse.notally.viewmodels.NotallyModel

class FileProgress(
    val inProgress: Boolean,
    val current: Int,
    val total: Int,
    val fileType: NotallyModel.FileType,
)
