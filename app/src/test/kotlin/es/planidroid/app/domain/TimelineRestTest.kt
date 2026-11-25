package es.planidroid.app.domain
import java.time.*
import org.junit.Test
import org.junit.Assert.*
class TimelineRestTest{
    private val date=LocalDate.of(2027,1,4);private val zone=ZoneOffset.UTC
    private fun at(d:LocalDate,h:Int,m:Int=0)=d.atTime(h,m).toInstant(zone).toEpochMilli()
    @Test fun midnightSessionsAppearOnBothDays(){
        val span=TimelineSpan("night",at(date,22),at(date.plusDays(1),2))
        val a=TimelineLayout.place(listOf(span),TimelineLayout.bounds(date,zone)).single()
        val b=TimelineLayout.place(listOf(span),TimelineLayout.bounds(date.plusDays(1),zone)).single()
        assertEquals(120L,(a.end-a.start)/60000);assertEquals(120L,(b.end-b.start)/60000)
    }
    @Test fun overlapGetsSeparateColumns(){
        val p=TimelineLayout.place(listOf(TimelineSpan("a",at(date,10),at(date,12)),TimelineSpan("b",at(date,11),at(date,13)),TimelineSpan("c",at(date,14),at(date,15))),TimelineLayout.bounds(date,zone))
        assertEquals(2,p[0].lanes);assertEquals(2,p[1].lanes);assertNotEquals(p[0].lane,p[1].lane);assertEquals(1,p[2].lanes)
    }
    @Test fun savedBreakDoesNotReduceStudyTime(){
        val task=Task(id="t",title="Estudio",minutes=240,planHints=PlanHints(windowStart="21:00",windowEnd="06:00",sessionLimit=120,breakMinutes=15,breakReason="Gemini recomienda una pausa"))
        val proposal=AgendaPlanner.plan(listOf(AgendaInput(task.id,task.title,240,date.plusDays(1),0)),date,date.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,180,emptyList(),zone,at(date,20),tasks=listOf(task))
        val sessions=proposal.sessions()
        assertTrue(sessions.any{it.isBreak})
        assertTrue(sessions.filter{it.isBreak}.all{it.taskId==task.id})
        assertEquals(240L,sessions.filterNot{it.isBreak}.sumOf{(it.end-it.start)/60000})
        assertEquals(240,TaskConstraints.remaining(task,sessions.filter{it.isBreak}))
        assertEquals(0,TaskConstraints.remaining(task,sessions))
        assertFalse(proposal.sessions(false).any{it.isBreak})
        assertTrue(decode<Session>(encode(sessions.first{it.isBreak})).isBreak)
    }
    @Test fun restBlocksEvenIfTaskOverlapAllowed(){
        val a=Task(id="a",title="A",overlapWith=listOf("b"));val b=Task(id="b",title="B")
        val rest=Session(taskId="a",start=at(date,10),end=at(date,10,15),isBreak=true)
        val work=Session(taskId="b",start=at(date,10),end=at(date,11))
        assertTrue(OverlapPolicy.conflict(rest,work,listOf(a,b)))
    }
    @Test fun compactContextMergesBusyIntervals(){
        val a=Session(taskId="a",start=at(date,10),end=at(date,11))
        val result=AgendaContext.availability(listOf(a),listOf(Interval(at(date,11),at(date,12))),date,date,zone)
        assertTrue(result.contains("T10:00/2027-01-04T12:00"))
        assertFalse(result.contains("T11:00"))
    }
}
