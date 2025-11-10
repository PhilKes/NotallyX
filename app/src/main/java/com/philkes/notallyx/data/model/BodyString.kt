package com.philkes.notallyx.data.model

@JvmInline
value class BodyString(val value: String) {
    override fun toString(): String = value

    fun isEmpty(): Boolean = value.isEmpty()

    fun isNotEmpty(): Boolean = value.isNotEmpty()

    fun contains(other: String, ignoreCase: Boolean = false): Boolean =
        value.contains(other, ignoreCase)
}
