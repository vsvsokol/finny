// java.util.TreeMap (sortedMapOf) из domain/tasks/TaskEngines.kt — на wasm его нет.
// Карта здесь сама держит ключи по возрастанию: перед каждым чтением порядка
// содержимое пересобирается по ключам, так что поведение совпадает с TreeMap.
package ru.finney.pet.domain.tasks

internal fun <K : Comparable<K>, V> sortedMapOf(): MutableMap<K, V> = SortedMutableMap()

private class SortedMutableMap<K : Comparable<K>, V>(
    private val inner: LinkedHashMap<K, V> = LinkedHashMap(),
) : MutableMap<K, V> by inner {

    private fun sort() {
        if (inner.keys.zipWithNext().all { (a, b) -> a <= b }) return
        val sorted = inner.entries.sortedBy { it.key }.map { it.key to it.value }
        inner.clear()
        sorted.forEach { (k, v) -> inner[k] = v }
    }

    override val keys: MutableSet<K> get() = sort().let { inner.keys }
    override val values: MutableCollection<V> get() = sort().let { inner.values }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = sort().let { inner.entries }
}
