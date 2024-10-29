package com.philkes.notallyx.data.model

import kotlinx.parcelize.Parcelize

@Parcelize
data class FileAttachment(var localName: String, var originalName: String, var mimeType: String) :
    Attachment
