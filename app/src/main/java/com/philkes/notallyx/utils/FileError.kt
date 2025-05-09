package com.philkes.notallyx.utils

import com.philkes.notallyx.presentation.viewmodel.NotallyModel

data class FileError(
    val name: String,
    val description: String,
    val fileType: NotallyModel.FileType,
)
