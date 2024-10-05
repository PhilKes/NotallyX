package com.omgodse.notally.model

data class SpanRepresentation(
    var bold: Boolean,
    var link: Boolean,
    var italic: Boolean,
    var monospace: Boolean,
    var strikethrough: Boolean,
    var start: Int,
    var end: Int,
) {

    fun isNotUseless(): Boolean {
        return bold || link || italic || monospace || strikethrough
    }

    fun isEqualInSize(representation: SpanRepresentation): Boolean {
        return start == representation.start && end == representation.end
    }
}
