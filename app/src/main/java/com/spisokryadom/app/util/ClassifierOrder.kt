package com.spisokryadom.app.util

/** Dictionaries have a stable full alphabet; pin order never changes that alphabet or local routes. */
fun <T> alphabetical(values:List<T>,name:(T)->String):List<T> = values.sortedWith(
    compareBy<T>{FuzzySearch.normalize(name(it))}.thenBy{name(it)}
)
