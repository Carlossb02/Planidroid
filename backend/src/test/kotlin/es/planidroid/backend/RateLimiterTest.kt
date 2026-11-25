package es.planidroid.backend
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
class RateLimiterTest {
    @Test fun quotaAndExpiry() {
        val limiter = RateLimiter(2, 1000)
        assertTrue(limiter.allow("a", 0)); assertTrue(limiter.allow("a", 1))
        assertFalse(limiter.allow("a", 2)); assertTrue(limiter.allow("b", 2))
        assertTrue(limiter.allow("a", 1000)); assertFalse(limiter.allow("a", 1000))
    }
}
