package es.planidroid.app.domain
import kotlinx.serialization.Serializable
import java.time.*
import java.text.Normalizer

@Serializable data class PlanHints(val dailyMinutes:Int=0,val excludedWeekdays:List<Int> = emptyList(),val suggestedMinutes:Int=0,val explanation:String="")

object SmartCapture {
    private fun normalize(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    private val number="(\\d{1,4}(?:[.,]\\d{1,2})?|una?|dos|tres|cuatro|cinco|seis|media)"
    private val duration=Regex("\\b$number\\s*(horas?|h|minutos?|min)\\b")
    private val repeat=Regex("^\\s*(al dia|cada dia|por dia|diari[ao]s?|todos los dias|a la semana|por semana|cada semana)")
    private fun minutes(m:MatchResult):Int {
        val n=m.groupValues[1].replace(',','.').toDoubleOrNull() ?: mapOf("un" to 1.0,"una" to 1.0,"dos" to 2.0,"tres" to 3.0,"cuatro" to 4.0,"cinco" to 5.0,"seis" to 6.0,"media" to .5).getOrDefault(m.groupValues[1],0.0)
        return (n*(if(m.groupValues[2].startsWith("h"))60 else 1)).toInt().coerceIn(0,100000)
    }
    fun totalMinutes(text:String):Int {val s=normalize(text);return duration.findAll(s).firstOrNull{!repeat.containsMatchIn(s.substring(it.range.last+1))}?.let(::minutes)?:0}
    fun hints(text:String,due:String,today:LocalDate):PlanHints {
        val s=normalize(text)
        val daily=duration.findAll(s).firstOrNull{m->Regex("^\\s*(al dia|cada dia|por dia|diari[ao]s?|todos los dias)").containsMatchIn(s.substring(m.range.last+1))}?.let(::minutes)?.coerceAtMost(1440)?:0
        val names=listOf("lunes","martes","miercoles","jueves","viernes","sabado","domingo")
        val exclusions=names.mapIndexedNotNull{i,n->if(Regex("(?:descansar|excepto|sin|no (?:puedo|estudiar|trabajar))[^.;,]{0,35}\\b$n(?:s)?\\b").containsMatchIn(s))i+1 else null}.toMutableSet()
        if(Regex("(?:excepto|sin|descansar|no (?:puedo|trabajar|estudiar))[^.;,]{0,35}fin(?:es)? de semana").containsMatchIn(s))exclusions.addAll(listOf(6,7))
        val deadline=runCatching{LocalDate.parse(due)}.getOrNull()
        val days=if(deadline!=null && deadline>=today && deadline<=today.plusDays(366)) (0..java.time.temporal.ChronoUnit.DAYS.between(today,deadline)).count{today.plusDays(it).dayOfWeek.value !in exclusions} else 0
        val topics=Regex("\\b(\\d{1,3})\\s+temas\\b").find(s)?.groupValues?.get(1)?.toIntOrNull()
        val explicit=totalMinutes(text)
        val estimate=when { explicit>0->0;topics!=null->(topics*45).coerceAtMost(100000);daily>0&&days>0->(daily*days).coerceAtMost(100000);deadline!=null&&Regex("\\b(estudiar|examen|preparar|repasar)\\b").containsMatchIn(s)->120;else->0 }
        val reason=when { explicit>0->"";topics!=null->"Estimación orientativa: 45 min por tema. Ajusta si necesitas más.";daily>0&&days>0->"Propuesta: $daily min por día disponible hasta la fecha límite.";estimate>0->"Estimación inicial de 2 h, pendiente de confirmar.";else->"" }
        return PlanHints(daily,exclusions.sorted(),estimate,reason)
    }
}
object SmartPlan {
    fun propose(task:Task,existing:List<Session>,external:List<Interval>,start:LocalDate,deadline:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,maxSession:Int,minutes:Int,dailyLimit:Int=0,zone:ZoneId=ZoneId.systemDefault(),now:Long=System.currentTimeMillis()):PlanResult {
        require(minutes in 1..100000 && deadline>=start && deadline<=start.plusDays(366) && days.isNotEmpty() && until>from && maxSession in 1..1440 && dailyLimit in 0..1440)
        val available=(0..java.time.temporal.ChronoUnit.DAYS.between(start,deadline)).map{start.plusDays(it)}.filter{it.dayOfWeek in days}
        var remaining=minutes
        val busy=existing.map{Interval(it.start,it.end)}+external
        val blocks=mutableListOf<Interval>()
        fun used(date:LocalDate)=existing.filter{it.taskId==task.id&&Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()==date}.sumOf{((it.end-it.start)/60000).toInt()}+blocks.filter{Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()==date}.sumOf{((it.end-it.start)/60000).toInt()}
        fun allocate(date:LocalDate,wanted:Int){
            val cap=if(dailyLimit>0)(dailyLimit-used(date)).coerceAtLeast(0) else remaining
            val target=minOf(remaining,wanted,cap)
            if(target>0){val r=Planner.plan(PlanRequest(target,date,date,days,from,until,maxSession,busy+blocks,zone,now));blocks+=r.intervals;remaining-=target-r.missingMinutes}
        }
        available.forEachIndexed{index,date->if(remaining>0){val fair=(remaining+available.size-index-1)/(available.size-index);allocate(date,maxOf(fair,minOf(30,remaining)))}}
        for(date in available){if(remaining==0)break;allocate(date,remaining)}
        return PlanResult(blocks.sortedBy{it.start},remaining)
    }
}
