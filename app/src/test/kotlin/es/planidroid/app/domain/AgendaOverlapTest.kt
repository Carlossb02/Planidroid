package es.planidroid.app.domain
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AgendaOverlapTest {
    private val day=LocalDate.of(2026,12,1)
    private val zone=ZoneOffset.UTC
    private fun plan(tasks:List<Task>,busy:List<Interval> = emptyList()):AgendaProposal =
        AgendaPlanner.plan(tasks.map{AgendaInput(it.id,it.title,60,day,0)},day,day,DayOfWeek.entries.toSet(),LocalTime.of(9,0),LocalTime.of(10,0),60,busy,zone,0,tasks=tasks)
    @Test fun defaultNoOverlap(){
        val a=Task(id="a",title="A")
        val b=Task(id="b",title="B")
        val p=plan(listOf(a,b))
        assertEquals(1,p.blocks.size)
        assertEquals(60,p.deficits.sumOf{it.minutes})
    }
    @Test fun onlyExplicitPairCanOverlap(){
        val a=Task(id="a",title="A",overlapWith=listOf("b"))
        val b=Task(id="b",title="B")
        val c=Task(id="c",title="C")
        val p=plan(listOf(a,b,c))
        assertEquals(setOf("a","b"),p.blocks.map{it.taskId}.toSet())
        assertEquals("c",p.deficits.single().taskId)
        assertFalse(OverlapPolicy.allowed("a","a",listOf(a,b,c)))
        assertFalse(OverlapPolicy.allowed("a","c",listOf(a,b,c)))
    }
    @Test fun externalEventsRemainBlocked(){
        val a=Task(id="a",title="A",overlapWith=listOf("b"))
        val b=Task(id="b",title="B")
        val from=day.atTime(9,0).toInstant(zone).toEpochMilli()
        val p=plan(listOf(a,b),listOf(Interval(from,from+3600000)))
        assertTrue(p.blocks.isEmpty())
        assertEquals(120,p.deficits.sumOf{it.minutes})
    }
    @Test fun removingPermissionCannotLeaveFutureConflict(){
        val a=Task(id="a",title="A",overlapWith=listOf("b"))
        val b=Task(id="b",title="B")
        val ss=listOf(Session(taskId="a",start=100,end=200),Session(taskId="b",start=100,end=200))
        assertTrue(OverlapPolicy.revokedConflict(ss,listOf(a,b),listOf(a.copy(overlapWith=emptyList()),b),0))
    }
}
