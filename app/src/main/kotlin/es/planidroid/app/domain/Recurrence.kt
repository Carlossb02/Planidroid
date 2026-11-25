package es.planidroid.app.domain
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.Serializable
@Serializable enum class Recurrence(val label:String){NONE("No repetir"),DAILY("Cada día"),WEEKLY("Cada semana"),MONTHLY("Cada mes"),YEARLY("Cada año")}
object RepeatingTasks {
    fun next(task:Task):Task? {if(task.recurrence==Recurrence.NONE||task.status!=Status.DONE||task.due.isBlank())return null;val due=LocalDate.parse(task.due);val next=when(task.recurrence){Recurrence.NONE->return null;Recurrence.DAILY->due.plusDays(1);Recurrence.WEEKLY->due.plusWeeks(1);Recurrence.MONTHLY->due.plusMonths(1);Recurrence.YEARLY->due.plusYears(1)};val root=task.seriesId.ifBlank{task.id};val id=UUID.nameUUIDFromBytes("planidroid-repeat:$root:$next".toByteArray(Charsets.UTF_8)).toString();val days=java.time.temporal.ChronoUnit.DAYS.between(due,next);return task.copy(id=id,seriesId=root,status=Status.PENDING,due=next.toString(),startDate=task.startDate.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).plusDays(days).toString()}.orEmpty(),actualMinutes=0,checks=task.checks.map{it.copy(id=newId(),done=false)},phases=task.phases.map{p->p.copy(id=newId(),done=false,actualMinutes=0,target=p.target.takeIf{it.isNotBlank()}?.let{LocalDate.parse(it).plusDays(days).toString()}.orEmpty(),checks=p.checks.map{it.copy(id=newId(),done=false)})})}
}
