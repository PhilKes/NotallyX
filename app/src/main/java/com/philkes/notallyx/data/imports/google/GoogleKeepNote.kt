package com.philkes.notallyx.data.imports.google

import com.philkes.notallyx.data.model.BaseNote
import kotlinx.serialization.Serializable

@Serializable
data class GoogleKeepNote(
    val attachments: List<GoogleKeepAttachment> = listOf(),
    val color: String = BaseNote.COLOR_DEFAULT,
    val isTrashed: Boolean = false,
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val textContent: String = "",
    val textContentHtml: String = "",
    val title: String = "",
    val labels: List<GoogleKeepLabel> = listOf(),
    val userEditedTimestampUsec: Long = System.currentTimeMillis(),
    val createdTimestampUsec: Long = System.currentTimeMillis(),
    val listContent: List<GoogleKeepListItem> = listOf(),
)

@Serializable data class GoogleKeepLabel(val name: String)

@Serializable data class GoogleKeepAttachment(val filePath: String, val mimetype: String)

@Serializable data class GoogleKeepListItem(val text: String, val isChecked: Boolean)
