package android.os

import kotlin.time.TimeSource

/** Монотонные часы, как на Android: HoldRepeat и PetAnimation меряют ими интервалы. */
object SystemClock {
    private val start = TimeSource.Monotonic.markNow()

    fun uptimeMillis(): Long = start.elapsedNow().inWholeMilliseconds
}
