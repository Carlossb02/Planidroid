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
        candidate.id!=other.id && candidate.start<other.end && other.start<candidate.end && (candidate.isBreak||other.isBreak||!allowed(candidate.taskId,other.taskId,tasks))
    fun revokedConflict(sessions:List<Session>,before:Collection<Task>,after:Collection<Task>,now:Long):Boolean {
        val future=sessions.filter{!it.done&&it.end>now}.sortedBy{it.start}
        for(i in future.indices)for(j in i+1 until future.size){
            val a=future[i];val b=future[j]
            if(b.start>=a.end)break
            if(!a.isBreak&&!b.isBreak&&allowed(a.taskId,b.taskId,before)&&conflict(a,b,after))return true
        }
        return false
    }
    fun blocking(taskId:String,sessions:List<Session>,tasks:Collection<Task>):List<Interval> =
        sessions.filter{it.isBreak||!allowed(taskId,it.taskId,tasks)}.map{Interval(it.start,it.end)}
}
