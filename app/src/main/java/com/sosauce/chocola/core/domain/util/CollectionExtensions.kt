package com.sosauce.chocola.core.domain.util

inline fun <E> List<E>.copyMutate(block: MutableList<E>.() -> Unit): List<E> {
    return toMutableList().apply(block)
}

inline fun <E> Set<E>.copyMutate(block: MutableSet<E>.() -> Unit): Set<E> {
    return toMutableSet().apply(block)
}


inline fun <T> List<T>.thenIf(
    condition: Boolean,
    crossinline block: List<T>.() -> List<T>
): List<T> = if (condition) block() else this


fun String.regex(matchCase: Boolean): Regex {
    return if (matchCase) {
        toRegex()
    } else {
        toRegex(RegexOption.IGNORE_CASE)
    }
}

fun <E> MutableSet<E>.addOrRemove(element: E) {
    if (!add(element)) {
        remove(element)
    }
}
