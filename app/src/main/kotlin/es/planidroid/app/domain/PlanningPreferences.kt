package es.planidroid.app.domain

import java.time.*

/** A night window belongs to its starting weekday; midnight is not a boundary inside it. */
object PlanningPreferences {
    fun explicitWindow(text:String):Pair<String,String>? {
        val s=StudyIntent.normalize(text)
        val match=Regex("""\b(?:de|desde|entre)\s+(\d{1,2})(?::(\d{2}))?\s*(?:h|horas?)?\s*(?:a|hasta|y|-)\s*(\d{1,2})(?::(\d{2}))?\s*(?:h|horas?)?(?!\d)""").find(s)
        if(match!=null){
            fun value(i:Int):String?=runCatching{LocalTime.of(match.groupValues[i].toInt(),match.groupValues[i+1].ifBlank{"0"}.toInt()).toString()}.getOrNull()
            val from=value(1);val until=value(3)
            if(from!=null&&until!=null&&from!=until&&(match.value.contains(":")||Regex("""\b(?:h|horas?)\b""").containsMatchIn(match.value)||Regex("""\b(?:horario|noche|madrugada|estudiar|estudio|disponible)\b""").containsMatchIn(s)))return from to until
        }
        // Do not interpret negative availability as a preferred window.
        if(Regex("""\b(?:no puedo|no quiero|excepto|sin)\b[^.;]{0,45}\b(?:noche|madrugada|manana|tarde)\b""").containsMatchIn(s))return null
        val night=Regex("""\b(?:noche|noches|nocturn[oa]s?)\b""").containsMatchIn(s)
        val early=Regex("""\b(?:madrugada|madrugadas)\b""").containsMatchIn(s)
        return when {
            night&&early->"21:00" to "06:00"
            night->"21:00" to "00:00"
            early->"00:00" to "06:00"
            Regex("""\b(?:por la|por las|solo de|estudio de)\s+mananas?\b""").containsMatchIn(s)->"08:00" to "13:00"
            Regex("""\b(?:por la|por las|solo de|estudio de)\s+tardes?\b""").containsMatchIn(s)->"15:00" to "21:00"
            else->null
        }
    }
    fun validated(h:PlanHints):PlanHints{
        val from=runCatching{LocalTime.parse(h.windowStart)}.getOrNull()
        val until=runCatching{LocalTime.parse(h.windowEnd)}.getOrNull()
        val valid=from!=null&&until!=null&&from!=until
        return h.copy(dailyMinutes=h.dailyMinutes.coerceIn(0,1440),excludedWeekdays=h.excludedWeekdays.filter{it in 1..7}.distinct(),availableWeekdays=h.availableWeekdays.filter{it in 1..7}.distinct(),daysPerWeek=h.daysPerWeek.coerceIn(0,7),minutesPerTopic=h.minutesPerTopic.coerceIn(0,100000),topicCount=h.topicCount.coerceIn(0,200),suggestedMinutes=h.suggestedMinutes.coerceIn(0,100000),windowStart=if(valid)from.toString()else "",windowEnd=if(valid)until.toString()else "",sessionLimit=h.sessionLimit.coerceIn(15,480),breakMinutes=h.breakMinutes.coerceIn(0,60),explanation=h.explanation.take(500))
    }
    /** Explicit user text outranks an AI interpretation; keep AI fields absent from deterministic hints. */
    fun merge(ai:PlanHints,local:PlanHints,text:String):PlanHints {
        val a=validated(ai)
        val window=explicitWindow(text)
        return validated(a.copy(
            dailyMinutes=local.dailyMinutes.takeIf{it>0}?:a.dailyMinutes,
            excludedWeekdays=(a.excludedWeekdays+local.excludedWeekdays).distinct(),
            availableWeekdays=local.availableWeekdays.ifEmpty{a.availableWeekdays},
            daysPerWeek=local.daysPerWeek.takeIf{it>0}?:a.daysPerWeek,
            minutesPerTopic=local.minutesPerTopic.takeIf{it>0}?:a.minutesPerTopic,
            topicCount=local.topicCount.takeIf{it>0}?:a.topicCount,
            suggestedMinutes=a.suggestedMinutes.takeIf{it>0}?:local.suggestedMinutes,
            finishBeforeDeadline=a.finishBeforeDeadline||local.finishBeforeDeadline,
            windowStart=window?.first?:a.windowStart,windowEnd=window?.second?:a.windowEnd,
            explanation=a.explanation.ifBlank{local.explanation}
        ))
    }
    fun recovery(minutes:Int,base:Int):Int=if(base<=0)0 else when{minutes>=150->maxOf(base,20);minutes>=90->maxOf(base,15);minutes>=45->base;else->0}
    fun span(from:LocalTime,until:LocalTime):Int=if(from==until)1440 else ((until.toSecondOfDay()-from.toSecondOfDay()+86400)%86400)/60
    fun fits(s:Session,t:Task,zone:ZoneId=ZoneId.systemDefault()):Boolean {
        val h=t.planHints
        val from=runCatching{LocalTime.parse(h.windowStart)}.getOrNull()?:return true
        val until=runCatching{LocalTime.parse(h.windowEnd)}.getOrNull()?:return true
        val date=Instant.ofEpochMilli(s.start).atZone(zone).toLocalDate()
        return Planner.windows(date.minusDays(1),date.plusDays(1),StudyIntent.availableDays(h),from,until,zone,false).any{it.start<=s.start&&it.end>=s.end}
    }
}
