package es.planidroid.app.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class StudyIntentTest {
    private val today=LocalDate.of(2026,11,25)
    private val zone=ZoneOffset.UTC
    private val prompt="tengo un examen de matematicas el 24 de diciembre, puedo estudiar 3 dias a la semana y tardo 5 horas por tema."

    @Test fun exactUserExample(){
        val task=CaptureParser.parse(prompt,today).single()
        assertEquals("Examen de matemáticas (24 diciembre)",task.title)
        assertEquals("2026-12-24",task.due)
        assertEquals(0,task.minutes)
        assertEquals(300,task.planHints.minutesPerTopic)
        assertEquals(0,task.planHints.topicCount)
        assertEquals(3,task.planHints.daysPerWeek)
        assertEquals(setOf(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY,DayOfWeek.FRIDAY),StudyIntent.availableDays(task.planHints))
        assertTrue(task.planHints.finishBeforeDeadline)
        assertEquals(6,StudyIntent.topicsAnswer("Son 6 temas")!!)
        assertEquals(1800,StudyIntent.studyMinutes(task.planHints,6))
    }

    @Test fun knownTopics(){
        val t=CaptureParser.parse(prompt+" Son seis temas.",today).single()
        assertEquals(1800,t.minutes)
        assertEquals(6,t.planHints.topicCount)
    }

    @Test fun availabilityChangeKeepsEffortAndAvoidsBusyEvents(){
        val t=CaptureParser.parse(prompt,today).single()
        val h=StudyIntent.reviseAvailability("solo puedo miércoles y viernes",t.planHints)!!
        val allowed=setOf(DayOfWeek.WEDNESDAY,DayOfWeek.FRIDAY)
        assertEquals(allowed,StudyIntent.availableDays(h))
        assertEquals(300,h.minutesPerTopic)
        val total=StudyIntent.studyMinutes(h,6)
        val busy=Interval(today.atTime(9,0).toInstant(zone).toEpochMilli(),today.atTime(11,0).toInstant(zone).toEpochMilli())
        val result=SmartPlan.propose(t,emptyList(),listOf(busy),today,LocalDate.parse(t.due).minusDays(1),allowed,LocalTime.of(9,0),LocalTime.of(19,0),60,total,zone=zone,now=0)
        assertEquals(0,result.missingMinutes)
        assertEquals(total.toLong(),result.intervals.sumOf{(it.end-it.start)/60000})
        assertTrue(result.intervals.all{val d=Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate();d.dayOfWeek in allowed&&d<LocalDate.parse(t.due)})
        assertTrue(result.intervals.none{it.start<busy.end&&busy.start<it.end})
        assertTrue(result.intervals.zipWithNext().all{(a,b)->a.end<=b.start})
    }
    @Test fun revisedPlanSubtractsCompletedWork(){
        val t=CaptureParser.parse(prompt,today).single().copy(minutes=1800,actualMinutes=120)
        val old=Session(taskId=t.id,start=today.atTime(10,0).toInstant(zone).toEpochMilli(),end=today.atTime(11,0).toInstant(zone).toEpochMilli())
        val done=old.copy(id=newId(),done=true)
        assertEquals(1680,TaskConstraints.remaining(t,listOf(done)))
        assertEquals(1620,TaskConstraints.remaining(t,listOf(done,old),old.start-1))
    }
}
