package com.philkes.notallyx.data.model

import kotlinx.parcelize.Parcelize

@Parcelize data class Audio(var name: String, val duration: Long, val timestamp: Long) : Attachment
