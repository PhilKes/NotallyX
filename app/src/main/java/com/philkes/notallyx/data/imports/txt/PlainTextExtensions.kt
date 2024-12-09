package com.philkes.notallyx.data.imports.txt

import com.philkes.notallyx.data.model.ListItem

fun CharSequence.extractListItems(regex: Regex): List<ListItem> {
    return regex
        .findAll(this)
        .mapIndexedNotNull { idx, matchResult ->
            val isChild = matchResult.groupValues[1] != ""
            val isChecked = matchResult.groupValues[2] != ""
            val itemText = matchResult.groupValues[3]
            if (itemText.isNotBlank()) {
                ListItem(itemText.trimStart(), isChecked, isChild, idx, mutableListOf())
            } else null
        }
        .toList()
}

fun CharSequence.findListSyntaxRegex(
    checkContains: Boolean = false,
    plainNewLineAllowed: Boolean = false,
): Regex? {
    val checkCallback: (String) -> Boolean =
        if (checkContains) {
            { string -> startsWith(string) || contains(string, ignoreCase = true) }
        } else {
            { string -> startsWith(string) }
        }
    if (checkCallback("- [ ]") || checkCallback("- [x]")) {
        return "\n?(\\s*)-? ?\\[? ?([xX]?)\\]?(.*)".toRegex()
    }
    if (checkCallback("[ ]") || checkCallback("[✓]")) {
        return "\n?(\\s*)\\[? ?(✓?)\\]?(.*)".toRegex()
    }
    if (checkCallback("-") || checkCallback("*")) {
        return "\n?(\\s*)[-*]?\\s*()(.*)".toRegex()
    }
    if (plainNewLineAllowed && contains("\n")) {
        return "\n?(\\s*)()(.*)".toRegex()
    }
    return null
}
