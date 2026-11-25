package es.planidroid.app.domain
import java.time.*
object AgendaContext {
    /** Compact occupied intervals, with no calendar titles or private details. */
    fun availability(sessions:List<Session>,external:List<Interval>,start:LocalDate,end:LocalDate,zone:ZoneId=ZoneId.systemDefault()):String{
        val lo=start.atStartOfDay(zone).toInstant().toEpochMilli();val hi=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val merged=mutableListOf<Interval>()
        (sessions.map{Interval(it.start,it.end)}+external).filter{it.end>lo&&it.start<hi}.map{Interval(maxOf(it.start,lo),minOf(it.end,hi))}.sortedBy{it.start}.forEach{i->val last=merged.lastOrNull();if(last!=null&&last.end>=i.start)merged[merged.lastIndex]=last.copy(end=maxOf(last.end,i.end))else merged+=i}
        fun stamp(ms:Long)=Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()
        return "$start a $end; "+merged.take(100).joinToString(";"){stamp(it.start)+"/"+stamp(it.end)}+(if(merged.size>100)"; intervalos adicionales omitidos" else "")
    }
    fun build(tasks:List<Task>,sessions:List<Session>,external:List<Interval>,start:LocalDate,end:LocalDate,picked:Set<String>,zone:ZoneId=ZoneId.systemDefault(),now:Long=System.currentTimeMillis()):String{
        val first=start.atStartOfDay(zone).toInstant().toEpochMilli()
        val last=end.coerceAtMost(start.plusDays(366)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun stamp(ms:Long)=Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()
        val out=StringBuilder("Zona: "+zone+"; ventana: "+start+" a "+end+"; hora actual: "+stamp(now)+"\n")
        fun append(line:String){if(out.length+line.length<15000)out.append(line).append('\n')}
        tasks.sortedWith(compareByDescending<Task>{it.id in picked}.thenBy{it.due.ifBlank{"9999"}}).take(24).forEach{t->val h=t.planHints;append("ID="+t.id+"; "+t.title.take(120)+"; seleccionada="+(t.id in picked)+"; prioridad="+t.priority+"; vence="+t.due+"; minutos pendientes="+TaskConstraints.remaining(t,sessions.filter{it.done||it.start<=now})+"; horario="+h.windowStart+"-"+h.windowEnd+"; días="+StudyIntent.availableDays(h).joinToString{it.name}+"; movible="+sessions.any{it.taskId==t.id&&!it.done&&it.start>now&&it.calendarTitle.isBlank()})}
        external.filter{it.start<last&&it.end>first}.sortedBy{it.start}.take(70).forEach{append("CITA EXTERNA FIJA: "+stamp(it.start)+" a "+stamp(it.end))}
        sessions.filter{it.start<last&&it.end>first}.sortedBy{it.start}.take(120).forEach{append((if(it.isBreak)"DESCANSO " else "SESIÓN ")+it.taskId+": "+stamp(it.start)+" a "+stamp(it.end)+"; "+if(!it.done&&it.start>now&&it.calendarTitle.isBlank()&&tasks.any{t->t.id==it.taskId})"movible solo con confirmación" else "fija")}
        return out.toString()
    }
}
