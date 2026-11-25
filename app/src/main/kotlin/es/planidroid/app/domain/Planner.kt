package es.planidroid.app.domain
import java.time.*

data class Interval(val start: Long, val end: Long)
data class PlanRequest(val minutes: Int, val start: LocalDate, val deadline: LocalDate, val weekdays: Set<DayOfWeek>, val from: LocalTime, val until: LocalTime, val maxSession: Int, val busy: List<Interval>, val zone: ZoneId = ZoneId.systemDefault(), val minStart: Long = System.currentTimeMillis(),val breakMinutes:Int=0)
data class PlanResult(val intervals: List<Interval>, val missingMinutes: Int,val breaks:List<Interval> = emptyList())
object Planner {
    fun windows(start:LocalDate,deadline:LocalDate,days:Set<DayOfWeek>,from:LocalTime,until:LocalTime,zone:ZoneId,clip:Boolean=true):List<Interval>{
        if(deadline<start)return emptyList()
        val first=start.atStartOfDay(zone).toInstant().toEpochMilli()
        val last=deadline.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val overnight=until<=from
        val out=mutableListOf<Interval>()
        var day=if(overnight&&from!=LocalTime.MIDNIGHT)start.minusDays(1)else start
        while(day<=deadline){
            if(day.dayOfWeek in days){
                val a=day.atTime(from).atZone(zone).toInstant().toEpochMilli()
                val b=(if(overnight)day.plusDays(1)else day).atTime(until).atZone(zone).toInstant().toEpochMilli()
                val lo=if(clip)maxOf(a,first)else a;val hi=if(clip)minOf(b,last)else b
                if(hi>lo)out+=Interval(lo,hi)
            }
            day=day.plusDays(1)
        }
        return out
    }
    fun plan(r:PlanRequest):PlanResult {
        require(r.minutes in 1..100000&&r.maxSession in 1..1440&&r.breakMinutes in 0..60)
        require(r.deadline>=r.start&&r.deadline<=r.start.plusDays(366))
        return fill(r.minutes,windows(r.start,r.deadline,r.weekdays,r.from,r.until,r.zone),r.maxSession,r.busy,r.minStart,r.breakMinutes)
    }
    fun fill(minutes:Int,windows:List<Interval>,maxSession:Int,busy:List<Interval>,minStart:Long,breakMinutes:Int=0):PlanResult{
        var remaining=minutes;val result=mutableListOf<Interval>();val breaks=mutableListOf<Interval>()
        val occupied=busy.filter{it.end>it.start}.sortedBy{it.start}
        for(window in windows){
            if(remaining==0)break
            var cursor=maxOf(window.start,((minStart+59999)/60000)*60000)
            fun allocate(to:Long){
                while(remaining>0&&to-cursor>=60000){
                    val n=minOf(remaining,maxSession,((to-cursor)/60000).toInt())
                    val finish=cursor+n*60000L
                    result+=Interval(cursor,finish);remaining-=n;cursor=finish
                    val recovery=PlanningPreferences.recovery(n,breakMinutes)*60000L
                    if(remaining>0&&recovery>0&&to-cursor>=recovery+60000){
                        breaks+=Interval(cursor,cursor+recovery);cursor+=recovery
                    }else if(remaining>0&&recovery>0)break
                }
            }
            for(b in occupied){
                if(b.end<=cursor||b.start>=window.end)continue
                allocate(minOf(b.start,window.end));cursor=maxOf(cursor,b.end)
            }
            allocate(window.end)
        }
        return PlanResult(result,remaining,breaks)
    }
}
