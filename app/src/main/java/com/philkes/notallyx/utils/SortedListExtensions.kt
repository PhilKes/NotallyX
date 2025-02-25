package com.philkes.notallyx.utils

import androidx.recyclerview.widget.SortedList

fun <R, C> SortedList<R>.map(transform: (R) -> C): List<C> {
    return (0 until this.size()).map { transform.invoke(this[it]) }
}

fun <R, C> SortedList<R>.mapIndexed(transform: (Int, R) -> C): List<C> {
    return (0 until this.size()).mapIndexed { idx, it -> transform.invoke(idx, this[it]) }
}

fun <R> SortedList<R>.forEach(function: (item: R) -> Unit) {
    return (0 until this.size()).forEach { function.invoke(this[it]) }
}

fun <R> SortedList<R>.forEachIndexed(function: (idx: Int, item: R) -> Unit) {
    for (i in 0 until this.size()) {
        function.invoke(i, this[i])
    }
}

fun <R> SortedList<R>.filter(function: (item: R) -> Boolean): List<R> {
    val list = mutableListOf<R>()
    for (i in 0 until this.size()) {
        if (function.invoke(this[i] as R)) {
            list.add(this[i] as R)
        }
    }
    return list.toList()
}

fun <R> SortedList<R>.find(function: (item: R) -> Boolean): R? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return this[i]
        }
    }
    return null
}

fun <R> SortedList<R>.indexOfFirst(function: (item: R) -> Boolean): Int? {
    for (i in 0 until this.size()) {
        if (function.invoke(this[i])) {
            return i
        }
    }
    return null
}

val SortedList<*>.lastIndex: Int
    get() = this.size() - 1

val SortedList<*>.indices: IntRange
    get() = (0 until this.size())

fun SortedList<*>.isNotEmpty(): Boolean {
    return size() > 0
}

fun SortedList<*>.isEmpty(): Boolean {
    return size() == 0
}
