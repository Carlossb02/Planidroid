package es.planidroid.app.domain

import java.time.*

data class Interval(val start: Long, val end: Long)
data class PlanRequest(val minutes: Int, val start: LocalDate, val deadline: LocalDate, val weekdays: Set<DayOfWeek>, val from: LocalTime, val until: LocalTime, val maxSession: Int, val busy: List<Interval>, val zone: ZoneId = ZoneId.systemDefault(), val minStart: Long = System.currentTimeMillis())
data class PlanResult(val intervals: List<Interval>, val missingMinutes: Int)
object Planner {
    fun plan(r: PlanRequest): PlanResult {
        require(r.minutes in 1..100000 && r.maxSession in 1..1440 && r.until > r.from)
        require(!r.deadline.isBefore(r.start) && r.deadline <= r.start.plusDays(366))
        var remaining = r.minutes
        val result = mutableListOf<Interval>()
        val busy = r.busy.filter { it.end > it.start }.sortedBy { it.start }
        var day = r.start
        while (day <= r.deadline && remaining > 0) {
            if (day.dayOfWeek in r.weekdays) {
                val end = day.atTime(r.until).atZone(r.zone).toInstant().toEpochMilli()
                var cursor = maxOf(day.atTime(r.from).atZone(r.zone).toInstant().toEpochMilli(), ((r.minStart + 59999) / 60000) * 60000)
                fun fill(to: Long) {
                    while (remaining > 0 && to - cursor >= 60000) {
                        val n = minOf(remaining, r.maxSession, ((to-cursor)/60000).toInt())
                        val finish = cursor + n * 60000L
                        result += Interval(cursor, finish); remaining -= n; cursor = finish
                    }
                }
                for (b in busy) {
                    if (b.end <= cursor || b.start >= end) continue
                    fill(minOf(b.start, end)); cursor = maxOf(cursor, b.end)
                }
                fill(end)
            }
            day = day.plusDays(1)
        }
        return PlanResult(result, remaining)
    }
}
