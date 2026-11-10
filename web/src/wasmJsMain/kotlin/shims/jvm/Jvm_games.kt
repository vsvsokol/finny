package ru.finney.pet.ui.tasks.games

/** MutableMap.putIfAbsent из Java 8 — в SorterGame.kt. */
internal fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? =
    get(key) ?: run { put(key, value); null }
