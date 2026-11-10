package ru.finney.pet.ui.progress

/** Map.toSortedMap(comparator) из ProgressViewModel.kt: TreeMap на wasm нет; порядок тот же. */
internal fun <K, V> Map<K, V>.toSortedMap(comparator: Comparator<in K>): Map<K, V> =
    entries.sortedWith { a, b -> comparator.compare(a.key, b.key) }.associate { it.key to it.value }
