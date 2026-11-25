package es.planidroid.app.domain

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate

/** Interprets study-specific constraints without pretending an unknown syllabus is known. */
object StudyIntent {
    private val words=mapOf("un" to 1,"uno" to 1,"una" to 1,"dos" to 2,"tres" to 3,"cuatro" to 4,"cinco" to 5,"seis" to 6,"siete" to 7,"ocho" to 8,"nueve" to 9,"diez" to 10)
    private val number="(\\d{1,3}|un[ao]?|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)"
    private val weekdays=listOf("lunes","martes","miercoles","jueves","viernes","sabado","domingo")
    private val months=listOf("enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre")
    fun normalize(s:String)=Normalizer.normalize(s.lowercase(java.util.Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    private fun integer(s:String)=s.toIntOrNull()?:words[s]
    fun topicCount(text:String):Int? {
        val s=normalize(text)
        return Regex("\\b"+number+"\\s+temas?\\b").find(s)?.groupValues?.get(1)?.let(::integer)?.takeIf{it in 1..200}
    }
    fun topicsAnswer(text:String):Int?=topicCount(text)?:integer(normalize(text).trim())?.takeIf{it in 1..200}
    fun perTopic(text:String):Int {
        val s=normalize(text)
        val m=Regex("\\b(\\d{1,3}(?:[.,]\\d{1,2})?|un[ao]?|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media)\\s*(horas?|h|minutos?|min)\\s*(?:por|cada|para cada|/|el)\\s*tema\\b").find(s)?:return 0
        val n=m.groupValues[1].replace(',','.').toDoubleOrNull()?:if(m.groupValues[1]=="media")0.5 else integer(m.groupValues[1])?.toDouble()?:0.0
        return (n*(if(m.groupValues[2].startsWith("h"))60 else 1)).toInt().coerceIn(0,100000)
    }
    fun weeklyCount(text:String):Int?=Regex("\\b"+number+"\\s+dias?\\s*(?:a la|por|cada|en la)\\s+semana\\b").find(normalize(text))?.groupValues?.get(1)?.let(::integer)?.takeIf{it in 1..7}
    fun namedDays(text:String):Set<DayOfWeek> {
        val s=normalize(text)
        // Only treat weekday names as positive availability after an affirmative cue.
        val clause=Regex("(?:solo\\s+(?:puedo|tengo|estudio)|puedo(?:\\s+estudiar)?|disponib(?:le|ilidad)|estudiar(?:\\s+los)?|estudio(?:\\s+los)?|los)\\s+([^.;\\n]+)").findAll(s).map{it.groupValues[1]}.firstOrNull{part->weekdays.any{Regex("\\b"+it+"s?\\b").containsMatchIn(part)}&&!Regex("\\b(?:excepto|descansar|no puedo|sin)\\b").containsMatchIn(part)} ?: s.takeIf{Regex("^(?:los\\s+)?(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)s?(?:\\s*(?:,|y|e)\\s*(?:los\\s+)?(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)s?)*[.!]?$").matches(it.trim())}?:return emptySet()
        return weekdays.mapIndexedNotNull{i,w->if(Regex("\\b"+w+"s?\\b").containsMatchIn(clause))DayOfWeek.of(i+1)else null}.toSet()
    }
    fun proposedDays(count:Int,excluded:Set<DayOfWeek> = emptySet()):Set<DayOfWeek> {
        val order=listOf(DayOfWeek.MONDAY,DayOfWeek.WEDNESDAY,DayOfWeek.FRIDAY,DayOfWeek.SUNDAY,DayOfWeek.TUESDAY,DayOfWeek.THURSDAY,DayOfWeek.SATURDAY)
        return order.filterNot{it in excluded}.take(count.coerceIn(1,7)).toSet()
    }
    fun examTitle(text:String,due:String):String? {
        val match=Regex("\\bexamen\\s+de\\s+(.+?)(?=\\s+(?:el|para el|del)\\s+(?:\\d|hoy\\b|mañana\\b)|[,.;]|$)",RegexOption.IGNORE_CASE).find(text)?:return null
        val subject=match.groupValues[1].trim().lowercase(java.util.Locale("es","ES")).let{when(normalize(it)){"matematicas"->"matemáticas";"fisica"->"física";"quimica"->"química";"biologia"->"biología";"farmacologia"->"farmacología";else->it}}
        val date=runCatching{LocalDate.parse(due)}.getOrNull()
        return ("Examen de "+subject+(if(date==null)"" else " ("+date.dayOfMonth+" "+months[date.monthValue-1]+")")).take(180)
    }
    fun availableDays(h:PlanHints):Set<DayOfWeek> {
        val excluded=h.excludedWeekdays.filter{it in 1..7}.map{DayOfWeek.of(it)}.toSet()
        val explicit=h.availableWeekdays.filter{it in 1..7}.map{DayOfWeek.of(it)}.toSet()
        return when{explicit.isNotEmpty()->explicit-excluded;h.daysPerWeek>0->proposedDays(h.daysPerWeek,excluded);else->DayOfWeek.entries.toSet()-excluded}
    }
    fun reviseAvailability(text:String,h:PlanHints):PlanHints? {
        val days=namedDays(text)
        if(days.isNotEmpty())return h.copy(availableWeekdays=days.map{it.value}.sorted(),daysPerWeek=days.size,excludedWeekdays=h.excludedWeekdays.filterNot{n->days.any{it.value==n}})
        val count=weeklyCount(text)?:return null
        return h.copy(availableWeekdays=emptyList(),daysPerWeek=count)
    }
    fun studyMinutes(h:PlanHints,count:Int):Int {
        require(count in 1..200&&h.minutesPerTopic>0)
        return (count.toLong()*h.minutesPerTopic).also{require(it in 1..100000)}.toInt()
    }
}
