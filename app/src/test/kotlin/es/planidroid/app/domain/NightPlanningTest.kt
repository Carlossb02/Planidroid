package es.planidroid.app.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class NightPlanningTest{
    private val day=LocalDate.of(2027,1,4)
    private val zone=ZoneOffset.UTC
    private fun at(d:LocalDate,h:Int,m:Int=0)=d.atTime(h,m).toInstant(zone).toEpochMilli()
    @Test fun explicitNightOverridesMorningFromAi(){
        val text="estudio por la noche y madrugadas"
        val h=PlanningPreferences.merge(PlanHints(windowStart="09:00",windowEnd="19:00"),SmartCapture.hints(text,"",day),text)
        assertEquals("21:00",h.windowStart);assertEquals("06:00",h.windowEnd)
        assertEquals("22:30" to "04:00",PlanningPreferences.explicitWindow("estudio de 22:30 a 04:00"))
    }
    @Test fun longBlocksAndBreaksCrossMidnight(){
        val r=Planner.plan(PlanRequest(240,day,day.plusDays(1),setOf(DayOfWeek.MONDAY),LocalTime.of(22,0),LocalTime.of(4,0),120,emptyList(),zone,at(day,21),10))
        assertEquals(0,r.missingMinutes);assertEquals(2,r.intervals.size);assertEquals(240L,r.intervals.sumOf{(it.end-it.start)/60000})
        assertEquals(at(day,22),r.intervals.first().start);assertEquals(at(day.plusDays(1),0,15),r.intervals.last().start)
        assertEquals(15L,r.breaks.sumOf{(it.end-it.start)/60000})
    }
    @Test fun fixedAppointmentsAreNeverOverwritten(){
        val t=Task(id="night",title="Estudio",due=day.plusDays(1).toString(),minutes=240,planHints=PlanHints(windowStart="22:00",windowEnd="04:00",sessionLimit=120,breakMinutes=10,availableWeekdays=listOf(1)))
        val fixed=Interval(at(day.plusDays(1),0),at(day.plusDays(1),1))
        val result=AgendaPlanner.plan(listOf(AgendaInput(t.id,t.title,240,day.plusDays(1),0)),day,day.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,180,listOf(fixed),zone,at(day,21),tasks=listOf(t))
        assertEquals(0,result.deficits.size)
        assertTrue(result.blocks.none{it.start<fixed.end&&fixed.start<it.end})
        assertTrue(result.blocks.all{PlanningPreferences.fits(Session(taskId=t.id,start=it.start,end=it.end),t,zone)})
    }
    @Test fun examDayRemainsFree(){
        val t=Task(title="Examen",due=day.plusDays(1).toString(),planHints=PlanHints(finishBeforeDeadline=true,windowStart="22:00",windowEnd="04:00"))
        val r=AgendaPlanner.plan(listOf(AgendaInput(t.id,t.title,240,day.plusDays(1),0)),day,day.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,120,emptyList(),zone,at(day,21),tasks=listOf(t))
        assertTrue(r.blocks.all{it.end<=at(day.plusDays(1),0)});assertEquals(120,r.deficits.single().minutes)
    }
    @Test fun breaksBetweenPhases(){
        val t=Task(id="t",title="Estudio",planHints=PlanHints(windowStart="21:00",windowEnd="04:00",sessionLimit=120,breakMinutes=10))
        val r=AgendaPlanner.plan(listOf(AgendaInput(t.id,t.title,180,day.plusDays(1),0,listOf("a" to 90,"b" to 90))),day,day.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,180,emptyList(),zone,at(day,20),tasks=listOf(t))
        assertTrue(r.deficits.isEmpty());assertEquals(listOf("a","b"),r.blocks.map{it.phaseId});assertEquals(15L,(r.blocks[1].start-r.blocks[0].end)/60000)
    }
}
