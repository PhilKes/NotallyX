package com.philkes.notallyx.data.model

import java.io.Serializable

enum class Folder : Serializable {
    NOTES,
    DELETED,
    ARCHIVED;

    companion object {
        fun valueOfOrDefault(value: String) =
            try {
                valueOf(value)
            } catch (e: Exception) {
                NOTES
            }
    }
}
