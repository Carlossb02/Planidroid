package es.planidroid.app.domain

import java.time.*

data class AgendaInput(val taskId:String,val title:String,val minutes:Int,val deadline:LocalDate,val priority:Int,val phases:List<Pair<String,Int>> = emptyList(),val earliest:LocalDate?=null)
data class AgendaBlock(val taskId:String,val phaseId:String,val start:Long,val end:Long)
data class AgendaDeficit(val taskId:String,val title:String,val minutes:Int)
data class AgendaProposal(val blocks:List<AgendaBlock>,val deficits:List<AgendaDeficit>)
object AgendaPlanner {
    /** Earliest deadline first, with explicit per-task availability and pair-specific overlap permissions. */
    fun plan(input:List<AgendaInput>,start:LocalDate,end:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,maxSession:Int,busy:List<Interval>,zone:ZoneId=ZoneId.systemDefault(),minStart:Long=System.currentTimeMillis(),existing:List<Session> = emptyList(),tasks:List<Task> = emptyList()):AgendaProposal {
        require(end>=start && end<=start.plusDays(366) && days.isNotEmpty() && until>from && maxSession in 1..1440)
        val occupied=existing.toMutableList();val blocks=mutableListOf<AgendaBlock>();val deficits=mutableListOf<AgendaDeficit>()
        for(t in input.sortedWith(compareBy<AgendaInput>{it.deadline}.thenByDescending{it.priority}.thenBy{it.taskId})){
            if(t.minutes<=0)continue
            val task=tasks.find{it.id==t.taskId}?:Task(id=t.taskId,title=t.title)
            val h=task.planHints
            val allowed=days.intersect(StudyIntent.availableDays(h))
            val first=maxOf(start,t.earliest?:start)
            val limit=minOf(if(h.finishBeforeDeadline&&task.due.isNotBlank())minOf(t.deadline,LocalDate.parse(task.due).minusDays(1))else t.deadline,end)
            val earliestTime=maxOf(from,h.windowStart.takeIf{it.isNotBlank()}?.let{LocalTime.parse(it)}?:from)
            val latestTime=minOf(until,h.windowEnd.takeIf{it.isNotBlank()}?.let{LocalTime.parse(it)}?:until)
            if(limit<first||allowed.isEmpty()||latestTime<=earliestTime){deficits+=AgendaDeficit(t.taskId,t.title,t.minutes);continue}
            var left=t.minutes;var totalMissing=0;var cursor=minStart
            val parts=mutableListOf<Pair<String,Int>>()
            for((id,amount) in t.phases){val n=minOf(left,amount.coerceAtLeast(0));if(n>0){parts+=id to n;left-=n}}
            if(left>0)parts+="" to left
            for((id,n) in parts){
                if(totalMissing>0){totalMissing+=n;continue}
                val hardBusy=busy+OverlapPolicy.blocking(t.taskId,occupied,tasks)
                // Pass same-task sessions to SmartPlan for daily workload accounting.
                val own=occupied.filter{it.taskId==t.taskId}
                val result=SmartPlan.propose(task,own,hardBusy,first,limit,allowed,earliestTime,latestTime,minOf(maxSession,h.sessionLimit.coerceIn(1,1440)),n,h.dailyMinutes,zone,cursor,spread=parts.size==1)
                result.intervals.forEach{b->blocks+=AgendaBlock(t.taskId,id,b.start,b.end);occupied+=Session(taskId=t.taskId,phaseId=id,start=b.start,end=b.end)}
                cursor=result.intervals.lastOrNull()?.end?:cursor
                totalMissing+=result.missingMinutes
            }
            if(totalMissing>0)deficits+=AgendaDeficit(t.taskId,t.title,totalMissing)
        }
        return AgendaProposal(blocks.sortedBy{it.start},deficits)
    }
}
