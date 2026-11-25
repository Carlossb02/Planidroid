package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
class ZoneNamingTest {
    @Test fun defaultNameMatchesType(){
        ZoneType.entries.forEach{assertEquals(it.label,LayoutGrid.defaultName(it,emptyList()))}
    }
    @Test fun repeatedNamesAreUniqueAcrossDoorAndInterior(){
        val existing=listOf(Zone("Balda"),Zone("BALDA 2",door=true))
        assertEquals("Balda 3",LayoutGrid.defaultName(ZoneType.SHELF,existing))
        assertEquals("Cajón",LayoutGrid.defaultName(ZoneType.DRAWER,existing))
    }
    @Test fun resizingRespectsLimitsAndOtherZones(){
        val first=Zone("Balda",w=2,h=1)
        val second=Zone("Balda 2",x=2,w=2,h=1)
        assertFalse(LayoutGrid.fits(first.copy(w=3),listOf(first,second)))
        assertTrue(LayoutGrid.fits(first.copy(h=2),listOf(first,second)))
        assertFalse(LayoutGrid.fits(first.copy(h=9),listOf(first,second)))
    }
}
