package es.planidroid.app.domain
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class SmartCaptureTest {
    private val start=LocalDate.of(2026,11,25)
    @Test fun dailyLimitAndRestDays(){
        val t=CaptureParser.parse("Preparar examen el 30 de noviembre, 6 horas en total, una hora al día y descansar los domingos",start).single()
        assertEquals(360,t.minutes);assertEquals(60,t.planHints.dailyMinutes);assertTrue(7 in t.planHints.excludedWeekdays)
    }
    @Test fun spreadAndDeficit(){
        val zone=ZoneOffset.UTC;val t=Task(title="Estudiar")
        val busy=listOf(Interval(start.atTime(9,0).toInstant(zone).toEpochMilli(),start.atTime(10,0).toInstant(zone).toEpochMilli()))
        val result=SmartPlan.propose(t,emptyList(),busy,start,start.plusDays(3),DayOfWeek.entries.toSet(),LocalTime.of(9,0),LocalTime.of(11,0),60,240,60,zone,0)
        assertEquals(0,result.missingMinutes);assertEquals(4,result.intervals.map{Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()}.distinct().size)
        assertTrue(result.intervals.none{r->busy.any{it.start<r.end&&r.start<it.end}})
        val limited=SmartPlan.propose(t,emptyList(),emptyList(),start,start.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.of(9,0),LocalTime.of(10,0),60,180,60,zone,0)
        assertEquals(60,limited.missingMinutes)
    }
    @Test fun quantityIsOptionalAndIconsDiffer(){
        val items=ShoppingText.parse("Leche\n2 kg patatas\nHuevos 6","my-list")
        assertEquals(3,items.size);assertFalse(items[0].quantitySpecified);assertEquals("",items[0].unit)
        assertEquals(2.0,items[1].quantity,0.0);assertEquals("kg",items[1].unit);assertTrue(items[1].quantitySpecified)
        assertEquals(3,items.map{it.icon}.distinct().size);assertTrue(items.all{it.listId=="my-list"})
    }
}
