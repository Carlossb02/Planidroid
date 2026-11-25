package es.planidroid.app.domain
import java.time.LocalDate
object TaskConstraints {
    fun cycle(task:Task,tasks:List<Task>):Boolean {val graph=(tasks.filter{it.id!=task.id}+task).associateBy{it.id};val visiting=mutableSetOf<String>();val visited=mutableSetOf<String>();fun visit(id:String):Boolean{if(id in visiting)return true;if(id in visited)return false;visiting+=id;val loop=graph[id]?.dependencies.orEmpty().any{visit(it)};visiting-=id;visited+=id;return loop};return visit(task.id)}
    fun unmet(task:Task,tasks:List<Task>)=task.dependencies.filter{id->tasks.none{it.id==id&&it.status==Status.DONE}}
    fun remaining(task:Task,sessions:List<Session>):Int {val total=task.minutes.takeIf{it>0}?:task.phases.sumOf{it.minutes};val recorded=sessions.filter{it.taskId==task.id&&it.done}.sumOf{((it.end-it.start)/60000).toInt()};val future=sessions.filter{it.taskId==task.id&&!it.done}.sumOf{((it.end-it.start)/60000).toInt()};return (total-maxOf(recorded,task.actualMinutes)-future).coerceAtLeast(0)}
}
