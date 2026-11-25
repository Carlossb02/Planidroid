package es.planidroid.backend

import java.util.ArrayDeque
/** Per-process sliding-window quota, bounded by the configured allowlist. */
class RateLimiter(private val limit: Int = 30, private val windowMs: Long = 3_600_000L) {
    init { require(limit > 0 && windowMs > 0) }
    private val calls = mutableMapOf<String, ArrayDeque<Long>>()
    @Synchronized fun allow(uid: String, now: Long = System.currentTimeMillis()): Boolean {
        val times = calls.getOrPut(uid) { ArrayDeque() }
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= limit) return false
        times.addLast(now)
        return true
    }
}
