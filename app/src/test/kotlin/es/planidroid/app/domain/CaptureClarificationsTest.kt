package es.planidroid.app.domain
import org.junit.Test
import org.junit.Assert.*
import java.time.*
class CaptureClarificationsTest {
    @Test fun missingTopicsAnsweredLocally(){
        val t=Task(title="Examen",planHints=PlanHints(minutesPerTopic=120))
        val q=CaptureClarifications.questions(AiProposal(listOf(t))).single()
        assertEquals("topicCount",q.field)
        val result=CaptureClarifications.apply(t,q.field,"4")!!
        assertEquals(480,result.minutes);assertEquals(4,result.planHints.topicCount)
    }
    @Test fun capAndValidateQuestions(){
        val p=AiProposal(listOf(Task(title="Estudio")),clarifications=listOf(Clarification(9,"due","Fuera"),Clarification(0,"minutes","Duración"),Clarification(0,"window","Horario"),Clarification(0,"title","Título")))
        assertEquals(listOf("minutes","window"),CaptureClarifications.questions(p).map{it.field})
    }
    @Test fun sameDayWindowUsesOnlyRemainingTime(){
        val d=LocalDate.of(2027,1,4);val z=ZoneOffset.UTC
        val now=d.atTime(13,40).toInstant(z).toEpochMilli()
        val t=Task(title="Estudio",minutes=120,planHints=PlanHints(windowStart="13:00",windowEnd="15:00"))
        val result=AgendaPlanner.plan(listOf(AgendaInput(t.id,t.title,120,d.plusDays(1),0)),d,d.plusDays(1),DayOfWeek.entries.toSet(),LocalTime.MIDNIGHT,LocalTime.MIDNIGHT,120,emptyList(),z,now,tasks=listOf(t))
        assertEquals(now,result.blocks.first().start)
        assertEquals(80L,result.blocks.filter{it.start<d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()}.sumOf{(it.end-it.start)/60000})
        assertTrue(result.deficits.isEmpty())
    }
    @Test fun clarificationWindowAndDuration(){
        val t=Task(title="Estudiar")
        assertEquals("13:00",CaptureClarifications.apply(t,"window","13:00 a 15:00")!!.planHints.windowStart)
        assertEquals(120,CaptureClarifications.apply(t,"minutes","2 horas")!!.minutes)
        assertNull(CaptureClarifications.apply(t,"minutes","0"))
    }
}
