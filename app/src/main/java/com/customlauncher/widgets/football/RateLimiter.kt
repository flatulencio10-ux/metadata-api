package com.customlauncher.widgets.football

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sliding-window limiter: at most [maxRequests] per [windowMs]. [acquire] suspends until a
 * slot is free, so callers never exceed the football-data.org free tier (10 req/min).
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    suspend fun acquire() {
        while (true) {
            val wait = mutex.withLock {
                val now = clock()
                while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMs) timestamps.removeFirst()
                if (timestamps.size < maxRequests) {
                    timestamps.addLast(now)
                    0L
                } else {
                    windowMs - (now - timestamps.first())
                }
            }
            if (wait <= 0L) return
            delay(wait)
        }
    }

    /** Marks the window as exhausted (e.g. after an HTTP 429) for [ms]. */
    suspend fun backOff(ms: Long) = mutex.withLock {
        val until = clock() + ms - windowMs
        timestamps.clear()
        repeat(maxRequests) { timestamps.addLast(until) }
    }
}
