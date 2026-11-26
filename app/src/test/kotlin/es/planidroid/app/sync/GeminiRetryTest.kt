package es.planidroid.app.sync

import org.junit.Assert.*
import org.junit.Test

class GeminiRetryTest {
    @Test fun credentialsAndExhaustedQuotaDoNotTriggerAnotherRequest() {
        for(code in listOf(401,403,429)){
            assertEquals(GeminiRecovery.NONE,geminiHttpFailure(code,null).recovery)
        }
        assertEquals(GeminiRecovery.NONE,geminiHttpFailure(429,"80").recovery)
    }

    @Test fun briefRateLimitPreservesServerDelay() {
        val failure=geminiHttpFailure(429,"3")
        assertEquals(GeminiRecovery.RETRY,failure.recovery)
        assertEquals(3000L,failure.waitMillis)
    }

    @Test fun missingOrOverloadedModelCanSwitchWithoutRetryingInvalidCredentials() {
        assertEquals(GeminiRecovery.ALTERNATE,geminiHttpFailure(404,null).recovery)
        assertEquals(GeminiRecovery.ALTERNATE,geminiHttpFailure(503,null).recovery)
        assertEquals(GeminiRecovery.RETRY,geminiHttpFailure(504,null).recovery)
        assertEquals(GeminiRecovery.COMPATIBILITY,geminiHttpFailure(400,null).recovery)
    }
}
