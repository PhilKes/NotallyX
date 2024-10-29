package com.philkes.notallyx.data.imports.google

import com.philkes.notallyx.data.model.Color
import kotlinx.serialization.Serializable

@Serializable
data class KeepNote(
    val attachments: List<KeepAttachment>? = listOf(),
    val color: String? = Color.DEFAULT.name,
    val isTrashed: Boolean? = false,
    val isArchived: Boolean? = false,
    val isPinned: Boolean? = false,
    val textContent: String? = "",
    val title: String? = "",
    val labels: List<KeepLabel>? = listOf(),
    val userEditedTimestampUsec: Long? = System.currentTimeMillis(),
    val createdTimestampUsec: Long? = System.currentTimeMillis(),
    val listContent: List<KeepListItem>? = listOf(),
)

@Serializable data class KeepLabel(val name: String)

@Serializable data class KeepAttachment(val filePath: String, val mimetype: String)

@Serializable data class KeepListItem(val text: String, val isChecked: Boolean)
