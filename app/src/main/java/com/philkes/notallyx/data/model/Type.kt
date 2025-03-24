package com.philkes.notallyx.data.model

enum class Type {
    NOTE,
    LIST;

    companion object {
        fun valueOfOrDefault(value: String) =
            try {
                Type.valueOf(value)
            } catch (e: Exception) {
                NOTE
            }
    }
}
