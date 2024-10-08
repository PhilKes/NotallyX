package com.philkes.notallyx.model

import kotlinx.parcelize.Parcelize

@Parcelize
data class FileAttachment(var localName: String, var originalName: String, val mimeType: String) :
    Attachment
