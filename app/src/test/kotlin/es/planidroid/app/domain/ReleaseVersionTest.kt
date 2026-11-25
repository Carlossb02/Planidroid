package es.planidroid.app.domain
import org.junit.Test
import org.junit.Assert.*
class ReleaseVersionTest {
    @Test fun fourPartVersions() {
        assertTrue(ReleaseVersion.newer("v0.5.0.1", "0.5.0"))
        assertTrue(ReleaseVersion.newer("v0.5.0.10", "0.5.0.9"))
        assertTrue(ReleaseVersion.newer("v0.5.1.0", "0.5.0.99"))
        assertFalse(ReleaseVersion.newer("v0.5.0.0", "0.5.0"))
        assertFalse(ReleaseVersion.newer("v0.5.0.1", "0.5.0.2"))
        assertFalse(ReleaseVersion.newer("v0.5.0.1-beta", "0.5.0"))
        assertNull(ReleaseVersion.parse("0.5.0.01"))
        assertNull(ReleaseVersion.parse("0.5.0.1.0"))
        assertEquals(listOf("Planidroid_v0.5.0.1.apk", "Planidroid.apk"), ReleaseVersion.apkNames("v0.5.0.1"))
        assertTrue(ReleaseVersion.apkNames("v0.5.0.1-beta").isEmpty())
    }
    @Test fun firstPublicRelease() {
        assertTrue(ReleaseVersion.newer("v0.1.0.1", "0.1.0.0"))
        assertFalse(ReleaseVersion.newer("v0.1.0.0", "0.5.0.1"))
        assertEquals(listOf("Planidroid_v0.1.0.0.apk", "Planidroid.apk"), ReleaseVersion.apkNames("v0.1.0.0"))
    }
    @Test fun stableTags() {
        assertTrue(ReleaseVersion.newer("v0.10.0", "0.9.9"))
        assertFalse(ReleaseVersion.newer("v0.4.0", "0.4.0"))
        assertFalse(ReleaseVersion.newer("v0.3.0", "0.4.0"))
        assertFalse(ReleaseVersion.newer("v1.0.0-beta", "0.4.0"))
        assertFalse(ReleaseVersion.newer("v999999999999.0.0", "0.4.0"))
        assertTrue(ReleaseVersion.newer("1.0.0", "0.9.9"))
    }
}
