package es.planidroid.app.domain

/** Explicit permission between two different tasks; never permits external events or the same task. */
object OverlapPolicy {
    fun allowed(a:String,b:String,tasks:Collection<Task>):Boolean {
        if(a==b)return false
        val first=tasks.find{it.id==a}?:return false
        val second=tasks.find{it.id==b}?:return false
        return b in first.overlapWith || a in second.overlapWith
    }
    fun conflict(candidate:Session,other:Session,tasks:Collection<Task>):Boolean =
        candidate.id!=other.id && candidate.start<other.end && other.start<candidate.end && !allowed(candidate.taskId,other.taskId,tasks)
    fun blocking(taskId:String,sessions:List<Session>,tasks:Collection<Task>):List<Interval> =
        sessions.filterNot{allowed(taskId,it.taskId,tasks)}.map{Interval(it.start,it.end)}
}
