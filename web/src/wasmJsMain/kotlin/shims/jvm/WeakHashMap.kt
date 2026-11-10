// java.util.WeakHashMap из ui/room/RoomLight.kt. Слабых ссылок на wasm нет;
// обычная карта годится: там хранятся слои одной комнаты, их немного.
package java.util

class WeakHashMap<K, V> : MutableMap<K, V> by HashMap()
