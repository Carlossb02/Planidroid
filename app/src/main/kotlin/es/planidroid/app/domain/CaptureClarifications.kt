package es.planidroid.app.domain
import java.time.LocalDate
object CaptureClarifications {
    val fields=setOf("topicCount","minutes","due","window","title","detail")
    fun questions(p:AiProposal):List<Clarification>{
        val valid=p.clarifications.filter{it.taskIndex in p.tasks.indices&&it.field in fields&&it.question.isNotBlank()}.distinctBy{it.taskIndex to it.field}.take(2)
        if(valid.isNotEmpty())return valid
        return p.tasks.mapIndexedNotNull{i,t->if(t.planHints.minutesPerTopic>0&&t.planHints.topicCount==0)Clarification(i,"topicCount","¿Cuántos temas tienes que estudiar?")else null}.take(2)
    }
    fun apply(t:Task,field:String,answer:String,today:LocalDate=LocalDate.now()):Task?{
        val a=answer.trim();if(a.isBlank())return null
        return when(field){
            "topicCount"->{val n=StudyIntent.topicsAnswer(a)?:return null;if(n !in 1..200||t.planHints.minutesPerTopic<=0)return null;val h=t.planHints.copy(topicCount=n);val minutes=runCatching{StudyIntent.studyMinutes(h,n)}.getOrNull()?:return null;t.copy(minutes=minutes,planHints=h)}
            "minutes"->{val n=a.toIntOrNull()?:SmartCapture.totalMinutes(a);if(n !in 1..100000)null else t.copy(minutes=n)}
            "due"->{val d=runCatching{LocalDate.parse(a)}.getOrNull()?:return null;t.copy(due=d.toString(),category=Category.DEADLINE)}
            "window"->{val w=PlanningPreferences.explicitWindow(a)?:PlanningPreferences.explicitWindow("de "+a.replace("–"," a ").replace("-"," a "))?:return null;t.copy(planHints=t.planHints.copy(windowStart=w.first,windowEnd=w.second))}
            "title"->t.copy(title=a.take(180))
            "detail"->t.copy(description=listOf(t.description,a.take(1000)).filter{it.isNotBlank()}.joinToString("\n"))
            else->null
        }
    }
}
