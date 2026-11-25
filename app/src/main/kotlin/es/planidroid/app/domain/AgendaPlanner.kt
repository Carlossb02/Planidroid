package es.planidroid.app.domain

import java.time.*

data class AgendaInput(val taskId:String,val title:String,val minutes:Int,val deadline:LocalDate,val priority:Int,val phases:List<Pair<String,Int>> = emptyList(),val earliest:LocalDate?=null)
data class AgendaBlock(val taskId:String,val phaseId:String,val start:Long,val end:Long)
data class AgendaDeficit(val taskId:String,val title:String,val minutes:Int)
data class AgendaProposal(val blocks:List<AgendaBlock>,val deficits:List<AgendaDeficit>)
object AgendaPlanner {
    /** Earliest deadline first, priority breaks ties; sequential phase budgets; no overlap. */
    fun plan(input:List<AgendaInput>,start:LocalDate,end:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,maxSession:Int,busy:List<Interval>,zone:ZoneId=ZoneId.systemDefault(),minStart:Long=System.currentTimeMillis()):AgendaProposal {
        require(end>=start && end<=start.plusDays(366));val occupied=busy.toMutableList();val blocks=mutableListOf<AgendaBlock>();val deficits=mutableListOf<AgendaDeficit>()
        for(t in input.sortedWith(compareBy<AgendaInput>{it.deadline}.thenByDescending{it.priority}.thenBy{it.taskId})){
            if(t.minutes<=0)continue
            val first=maxOf(start,t.earliest?:start);val limit=minOf(t.deadline,end);if(limit<first){deficits+=AgendaDeficit(t.taskId,t.title,t.minutes);continue}
            var left=t.minutes;var totalMissing=0;var cursor=minStart
            val parts=mutableListOf<Pair<String,Int>>()
            for((id,amount) in t.phases){val n=minOf(left,amount.coerceAtLeast(0));if(n>0){parts+=id to n;left-=n}}
            if(left>0)parts+="" to left
            for((id,n) in parts){if(totalMissing>0){totalMissing+=n;continue};val result=Planner.plan(PlanRequest(n,first,limit,days,from,until,maxSession,occupied,zone,cursor));result.intervals.forEach{b->blocks+=AgendaBlock(t.taskId,id,b.start,b.end);occupied+=b};cursor=result.intervals.lastOrNull()?.end?:cursor;totalMissing+=result.missingMinutes}
            if(totalMissing>0)deficits+=AgendaDeficit(t.taskId,t.title,totalMissing)
        }
        return AgendaProposal(blocks.sortedBy{it.start},deficits)
    }
}
