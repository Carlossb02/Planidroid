package es.planidroid.app.domain

import java.time.*

data class AgendaInput(val taskId:String,val title:String,val minutes:Int,val deadline:LocalDate,val priority:Int,val phases:List<Pair<String,Int>> = emptyList(),val earliest:LocalDate?=null)
data class AgendaBlock(val taskId:String,val phaseId:String,val start:Long,val end:Long)
data class AgendaDeficit(val taskId:String,val title:String,val minutes:Int)
data class AgendaProposal(val blocks:List<AgendaBlock>,val deficits:List<AgendaDeficit>,val breaks:List<Interval> = emptyList())
object AgendaPlanner {
    /** Earliest deadline first, with explicit per-task availability and pair-specific overlap permissions. */
    fun plan(input:List<AgendaInput>,start:LocalDate,end:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,maxSession:Int,busy:List<Interval>,zone:ZoneId=ZoneId.systemDefault(),minStart:Long=System.currentTimeMillis(),existing:List<Session> = emptyList(),tasks:List<Task> = emptyList()):AgendaProposal {
        require(end>=start && end<=start.plusDays(366) && days.isNotEmpty() && maxSession in 1..1440)
        val occupied=existing.toMutableList();val blocks=mutableListOf<AgendaBlock>();val deficits=mutableListOf<AgendaDeficit>();val breaks=mutableListOf<Interval>()
        for(t in input.sortedWith(compareBy<AgendaInput>{it.deadline}.thenByDescending{it.priority}.thenBy{it.taskId})){
            if(t.minutes<=0)continue
            val task=tasks.find{it.id==t.taskId}?:Task(id=t.taskId,title=t.title)
            val h=task.planHints
            val allowed=days.intersect(StudyIntent.availableDays(h))
            val first=maxOf(start,t.earliest?:start)
            val limit=minOf(if(h.finishBeforeDeadline&&task.due.isNotBlank())minOf(t.deadline,LocalDate.parse(task.due).minusDays(1))else t.deadline,end)
            val earliestTime=h.windowStart.takeIf{it.isNotBlank()}?.let{LocalTime.parse(it)}?:if(from==until)LocalTime.of(9,0)else from
            val latestTime=h.windowEnd.takeIf{it.isNotBlank()}?.let{LocalTime.parse(it)}?:if(from==until)LocalTime.of(19,0)else until
            if(limit<first||allowed.isEmpty()){deficits+=AgendaDeficit(t.taskId,t.title,t.minutes);continue}
            val globalWindows=Planner.windows(first,limit,days,from,until,zone)
            val exclusions=if(from==until)emptyList()else buildList{var cursor=first.atStartOfDay(zone).toInstant().toEpochMilli();for(w in globalWindows){if(w.start>cursor)add(Interval(cursor,w.start));cursor=maxOf(cursor,w.end)};val last=limit.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();if(cursor<last)add(Interval(cursor,last))}
            var left=t.minutes;var totalMissing=0;var cursor=minStart
            val parts=mutableListOf<Pair<String,Int>>()
            for((id,amount) in t.phases){val n=minOf(left,amount.coerceAtLeast(0));if(n>0){parts+=id to n;left-=n}}
            if(left>0)parts+="" to left
            for((id,n) in parts){
                if(totalMissing>0){totalMissing+=n;continue}
                val hardBusy=busy+breaks+exclusions+OverlapPolicy.blocking(t.taskId,occupied,tasks)
                // Pass same-task sessions to SmartPlan for daily workload accounting.
                val own=occupied.filter{it.taskId==t.taskId}
                val result=SmartPlan.propose(task,own,hardBusy,first,limit,allowed,earliestTime,latestTime,minOf(maxSession,h.sessionLimit.coerceIn(1,1440)),n,h.dailyMinutes,zone,cursor,spread=parts.size==1&&!h.compact,breakMinutes=h.breakMinutes)
                result.intervals.forEach{b->blocks+=AgendaBlock(t.taskId,id,b.start,b.end);occupied+=Session(taskId=t.taskId,phaseId=id,start=b.start,end=b.end)}
                breaks+=result.breaks
                val tail=result.intervals.lastOrNull()
                cursor=tail?.end?:cursor
                if(tail!=null&&id!=parts.last().first&&result.missingMinutes==0){val pause=PlanningPreferences.recovery(((tail.end-tail.start)/60000).toInt(),h.breakMinutes)*60000L;val endPause=cursor+pause;if(pause>0&&hardBusy.none{it.start<endPause&&cursor<it.end}){breaks+=Interval(cursor,endPause);cursor=endPause}}
                totalMissing+=result.missingMinutes
            }
            if(totalMissing>0)deficits+=AgendaDeficit(t.taskId,t.title,totalMissing)
        }
        return AgendaProposal(blocks.sortedBy{it.start},deficits,breaks.sortedBy{it.start})
    }
}
