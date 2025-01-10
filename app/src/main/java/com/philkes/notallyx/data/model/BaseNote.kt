package com.philkes.notallyx.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["id", "folder", "pinned", "timestamp", "labels"])])
data class BaseNote(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val type: Type,
    val folder: Folder,
    val color: Color,
    val title: String,
    val pinned: Boolean,
    val timestamp: Long,
    val modifiedTimestamp: Long,
    val labels: List<String>,
    val body: String,
    val spans: List<SpanRepresentation>,
    val items: List<ListItem>,
    val images: List<FileAttachment>,
    val files: List<FileAttachment>,
    val audios: List<Audio>,
) : Item

fun BaseNote.deepCopy(): BaseNote {
    return copy(
        labels = labels.toMutableList(),
        spans = spans.map { it.copy() }.toMutableList(),
        items = items.map { it.copy() }.toMutableList(),
        images = images.map { it.copy() }.toMutableList(),
        files = files.map { it.copy() }.toMutableList(),
        audios = audios.map { it.copy() }.toMutableList(),
    )
}
