package es.planidroid.app.ui
import org.junit.Assert.*
import org.junit.Test
class NavigationHistoryTest{
    @Test fun backReturnsToActualEntry(){
        val fromHome=pushDestination(listOf("HOME"),"INVENTORY")
        assertEquals(listOf("HOME"),popDestination(fromHome))
        val fromSpaces=pushDestination(listOf("HOME","SPACES"),"INVENTORY")
        assertEquals(listOf("HOME","SPACES"),popDestination(fromSpaces))
    }
    @Test fun repeatedDestinationDoesNotDuplicate(){
        assertEquals(listOf("HOME","SHOPPING"),pushDestination(listOf("HOME","SHOPPING"),"SHOPPING"))
        assertEquals(listOf("HOME"),popDestination(listOf("HOME")))
    }
}
