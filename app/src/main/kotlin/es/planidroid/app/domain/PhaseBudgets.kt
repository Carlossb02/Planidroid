package es.planidroid.app.domain
object PhaseBudgets{
    fun remaining(task:Task,kept:List<Session>)=task.phases.filterNot{it.done}.map{p->p.id to (p.minutes-maxOf(p.actualMinutes,kept.filter{it.taskId==task.id&&it.phaseId==p.id&&it.done}.sumOf{((it.end-it.start)/60000).toInt()})-kept.filter{it.taskId==task.id&&it.phaseId==p.id&&!it.done}.sumOf{((it.end-it.start)/60000).toInt()}).coerceAtLeast(0)}
}
