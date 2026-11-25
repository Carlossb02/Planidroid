package es.planidroid.app.domain
import kotlinx.serialization.Serializable
import java.time.*
import java.text.Normalizer

@Serializable data class PlanHints(val dailyMinutes:Int=0,val excludedWeekdays:List<Int> = emptyList(),val suggestedMinutes:Int=0,val explanation:String="",val daysPerWeek:Int=0,val availableWeekdays:List<Int> = emptyList(),val minutesPerTopic:Int=0,val topicCount:Int=0,val finishBeforeDeadline:Boolean=false,val windowStart:String="",val windowEnd:String="",val sessionLimit:Int=120,val breakMinutes:Int=0,val compact:Boolean=true,val breakReason:String="")

object SmartCapture {
    private fun normalize(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
    private val number="(\\d{1,4}(?:[.,]\\d{1,2})?|una?|dos|tres|cuatro|cinco|seis|media)"
    private val duration=Regex("\\b$number\\s*(horas?|h|minutos?|min)\\b")
    private val repeat=Regex("^\\s*(al dia|cada dia|por dia|diari[ao]s?|todos los dias|a la semana|por semana|cada semana|por tema|cada tema|para cada tema|el tema|/tema)")
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
        val topics=StudyIntent.topicCount(text)
        val perTopic=StudyIntent.perTopic(text)
        val weekly=StudyIntent.weeklyCount(text)?:0
        val named=StudyIntent.namedDays(text)
        val exam=StudyIntent.examTitle(text,due)!=null
        val explicit=totalMinutes(text)
        val estimate=when { explicit>0->0;perTopic>0&&topics!=null->(perTopic.toLong()*topics).coerceAtMost(100000).toInt();perTopic>0->0;topics!=null->(topics*45).coerceAtMost(100000);daily>0&&days>0->(daily*days).coerceAtMost(100000);deadline!=null&&Regex("\\b(estudiar|examen|preparar|repasar)\\b").containsMatchIn(s)->120;else->0 }
        val reason=when { explicit>0->"";perTopic>0&&topics==null->"Falta el número de temas para calcular el tiempo total.";perTopic>0->"Tiempo calculado a partir del número de temas y la duración por tema.";topics!=null->"Estimación orientativa: 45 min por tema. Ajusta si necesitas más.";daily>0&&days>0->"Propuesta: $daily min por día disponible hasta la fecha límite.";estimate>0->"Estimación inicial de 2 h, pendiente de confirmar.";else->"" }
        val window=PlanningPreferences.explicitWindow(text)
        return PlanHints(daily,exclusions.sorted(),estimate,reason,if(named.isNotEmpty())named.size else weekly,named.map{it.value}.sorted(),perTopic,topics?:0,exam,window?.first.orEmpty(),window?.second.orEmpty())
    }
}
object SmartPlan {
    fun propose(task:Task,existing:List<Session>,external:List<Interval>,start:LocalDate,deadline:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,maxSession:Int,minutes:Int,dailyLimit:Int=0,zone:ZoneId=ZoneId.systemDefault(),now:Long=System.currentTimeMillis(),spread:Boolean=true,breakMinutes:Int=0):PlanResult {
        require(minutes in 1..100000 && deadline>=start && deadline<=start.plusDays(366) && days.isNotEmpty() && maxSession in 1..1440 && dailyLimit in 0..1440)
        val windows=Planner.windows(start,deadline,days,from,until,zone)
        var remaining=minutes
        val busy=existing.map{Interval(it.start,it.end)}+external
        val blocks=mutableListOf<Interval>();val breaks=mutableListOf<Interval>()
        fun allocate(window:Interval,wanted:Int){
            val used=(existing.filter{it.taskId==task.id&&!it.isBreak&&(it.done||it.end>now)}.map{Interval(it.start,it.end)}+blocks).sumOf{((minOf(it.end,window.end)-maxOf(it.start,window.start)).coerceAtLeast(0)/60000).toInt()}
            val cap=if(dailyLimit>0)(dailyLimit-used).coerceAtLeast(0)else remaining
            val target=minOf(remaining,wanted,cap)
            if(target>0){
                val r=Planner.fill(target,listOf(window),maxSession,busy+blocks+breaks,now,breakMinutes)
                blocks+=r.intervals;breaks+=r.breaks;remaining-=target-r.missingMinutes
            }
        }
        if(spread)windows.forEachIndexed{index,window->if(remaining>0){val fair=(remaining+windows.size-index-1)/(windows.size-index);allocate(window,maxOf(fair,minOf(30,remaining)))}}
        for(window in windows){if(remaining==0)break;allocate(window,remaining)}
        return PlanResult(blocks.sortedBy{it.start},remaining,breaks.sortedBy{it.start})
    }
}
