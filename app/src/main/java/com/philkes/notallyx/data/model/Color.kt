package com.philkes.notallyx.data.model

enum class Color {
    DEFAULT,
    CORAL,
    ORANGE,
    SAND,
    STORM,
    FOG,
    SAGE,
    MINT,
    DUSK,
    FLOWER,
    BLOSSOM,
    CLAY;

    companion object {
        fun allColorStrings() = entries.map { it.toColorString() }.toList()
    }
}

fun Color.toColorString() =
    when (this) {
        Color.DEFAULT -> BaseNote.COLOR_DEFAULT
        Color.CORAL -> "#FAAFA9"
        Color.ORANGE -> "#FFCC80"
        Color.SAND -> "#FFF8B9"
        Color.STORM -> "#AFCCDC"
        Color.FOG -> "#D3E4EC"
        Color.SAGE -> "#B4DED4"
        Color.MINT -> "#E2F6D3"
        Color.DUSK -> "#D3BFDB"
        Color.FLOWER -> "#F8BBD0"
        Color.BLOSSOM -> "#F5E2DC"
        Color.CLAY -> "#E9E3D3"
    }
