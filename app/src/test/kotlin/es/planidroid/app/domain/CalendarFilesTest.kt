package es.planidroid.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarFilesTest {
    private val madrid=ZoneId.of("Europe/Madrid")
    private fun read(vararg lines:String)=CalendarFiles.parse(
        (listOf("BEGIN:VCALENDAR","VERSION:2.0")+lines.toList()+"END:VCALENDAR").joinToString("\r\n").toByteArray(),madrid
    )

    @Test fun timedEventUsesNamedZoneAndUnfoldsEscapedText() {
        val event=read("BEGIN:VEVENT","UID:meeting","SUMMARY:Reunión\\, equipo",
            """DTSTART;TZID="America/New_York":20260514T090000""",
            "DTEND;TZID=America/New_York:20260514T100000","DESCRIPTION:Primera\\nSegunda",
            "  línea","LOCATION:Sala A","END:VEVENT").events.single()
        assertEquals(Instant.parse("2026-05-14T13:00:00Z").toEpochMilli(),event.start)
        assertEquals(Instant.parse("2026-05-14T14:00:00Z").toEpochMilli(),event.end)
        assertEquals("Reunión, equipo",event.title)
        assertEquals("Primera\nSegunda línea",event.notes)
        assertEquals("Sala A",event.location)
    }

    @Test fun planningIgnoresTransparentEventsButKeepsBusyAppointmentsFixed() {
        val appointment=Session(taskId="appointment",start=1000,end=3000,calendarTitle="Cita")
        val work=Session(taskId="work",start=2000,end=2500)
        val working=Task(id="work",title="Informe",overlapWith=listOf("appointment"))
        val busy=Task(id="appointment",title="Cita")
        val free=busy.copy(tags=listOf(CalendarFiles.FREE_TAG))
        assertTrue(OverlapPolicy.conflict(work,appointment,listOf(working,busy)))
        assertFalse(OverlapPolicy.conflict(work,appointment,listOf(working,free)))
        assertEquals(emptyList<Interval>(),OverlapPolicy.blocking("work",listOf(appointment),listOf(working,free)))
    }

    @Test fun transparentEventsKeepTheirAvailability() {
        val event=read("BEGIN:VEVENT","DTSTART:20261201T130000Z","TRANSP:TRANSPARENT","END:VEVENT").events.single()
        assertFalse(event.blocksTime)
    }

    @Test fun allDayHandlesTheEuropeanClockChangeAndUsesExclusiveEnd() {
        val allDay=read("BEGIN:VEVENT","UID:holiday","DTSTART;VALUE=DATE:20260329",
            "DTEND;VALUE=DATE:20260330","END:VEVENT").events.single()
        assertTrue(allDay.allDay)
        assertEquals(23*3600000L,allDay.end-allDay.start)
        val defaultEnd=read("BEGIN:VEVENT","DTSTART;VALUE=DATE:20260329","END:VEVENT").events.single()
        assertEquals(allDay.end,defaultEnd.end)
    }

    @Test fun cancellationInvalidDatesAndDuplicatesDoNotCreateExtraEvents() {
        val a=listOf("BEGIN:VEVENT","UID:one","DTSTART:20261201T130000Z","DURATION:PT1H30M","RRULE:FREQ=DAILY","END:VEVENT")
        val parsed=read(*(a+a+listOf("BEGIN:VEVENT","DTSTART:20260231T100000Z","END:VEVENT",
            "BEGIN:VEVENT","STATUS:CANCELLED","DTSTART:20261212T100000Z","END:VEVENT")).toTypedArray())
        assertEquals(3,parsed.skipped)
        assertTrue(parsed.recurrence)
        val event=parsed.events.single()
        assertEquals(90L*60000,event.end-event.start)
        assertEquals(event.sourceId,read(*a.toTypedArray()).events.single().sourceId)
    }

    @Test fun legacyVCalendarDecodesNamesAndFlagsUnknownZones() {
        val parsed=read("BEGIN:VEVENT","SUMMARY;ENCODING=QUOTED-PRINTABLE;CHARSET=ISO-8859-1:Reuni=F3n=",
            "=20final","DTSTART;TZID=Unknown:20261201T1230","END:VEVENT")
        assertTrue(parsed.assumedTimeZone)
        assertEquals("Reunión final",parsed.events.single().title)
    }

    @Test fun unrelatedContentAndOversizedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java){CalendarFiles.parse("name,start".toByteArray())}
        assertThrows(IllegalArgumentException::class.java){CalendarFiles.parse(ByteArray(CalendarFiles.MAX_BYTES+1))}
    }
}
