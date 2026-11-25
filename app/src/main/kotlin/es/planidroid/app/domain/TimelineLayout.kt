package es.planidroid.app.domain
import java.time.*

data class TimelineSpan(val id:String,val start:Long,val end:Long)
data class TimelinePlacement(val id:String,val start:Long,val end:Long,val lane:Int,val lanes:Int)
object TimelineLayout {
    fun bounds(day:LocalDate,zone:ZoneId):Interval=Interval(day.atStartOfDay(zone).toInstant().toEpochMilli(),day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    /** Clip midnight-spanning events and allocate overlapping events to distinct columns. */
    fun place(events:List<TimelineSpan>,day:Interval):List<TimelinePlacement>{
        val clipped=events.mapNotNull{v->val a=maxOf(v.start,day.start);val b=minOf(v.end,day.end);if(b>a)v.copy(start=a,end=b)else null}.sortedWith(compareBy<TimelineSpan>{it.start}.thenByDescending{it.end}.thenBy{it.id})
        val result=mutableListOf<TimelinePlacement>();val cluster=mutableListOf<TimelinePlacement>();val laneEnds=mutableListOf<Long>()
        var clusterEnd=Long.MIN_VALUE
        fun flush(){val count=laneEnds.size;result+=cluster.map{it.copy(lanes=count)};cluster.clear();laneEnds.clear()}
        for(v in clipped){
            if(v.start>=clusterEnd){flush();clusterEnd=Long.MIN_VALUE}
            var lane=laneEnds.indexOfFirst{it<=v.start}
            if(lane<0){lane=laneEnds.size;laneEnds+=v.end}else laneEnds[lane]=v.end
            cluster+=TimelinePlacement(v.id,v.start,v.end,lane,1);clusterEnd=maxOf(clusterEnd,v.end)
        }
        flush();return result
    }
}
