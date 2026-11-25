package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class PlannerTest {
    private val date=LocalDate.of(2027,1,4)
    private val zone=ZoneOffset.UTC
    private fun ms(h:Int)=date.atTime(h,0).toInstant(zone).toEpochMilli()
    @Test fun respectsBusyAndMaximum() {
        val r=Planner.plan(PlanRequest(120,date,date,setOf(DayOfWeek.MONDAY),LocalTime.of(9,0),LocalTime.of(13,0),45,listOf(Interval(ms(10),ms(11))),zone,0))
        assertEquals(0,r.missingMinutes);assertEquals(120L,r.intervals.sumOf{(it.end-it.start)/60000});assertTrue(r.intervals.all{it.end<=ms(10)||it.start>=ms(11)});assertTrue(r.intervals.all{it.end-it.start<=45*60000})
    }
    @Test fun insufficientTime() { val r=Planner.plan(PlanRequest(180,date,date,setOf(DayOfWeek.MONDAY),LocalTime.of(9,0),LocalTime.of(10,0),30,emptyList(),zone,0));assertEquals(120,r.missingMinutes) }
    @Test fun excludesUnavailableDays() { val r=Planner.plan(PlanRequest(30,date,date,setOf(DayOfWeek.TUESDAY),LocalTime.of(9,0),LocalTime.of(10,0),30,emptyList(),zone,0));assertEquals(30,r.missingMinutes);assertTrue(r.intervals.isEmpty()) }
}
