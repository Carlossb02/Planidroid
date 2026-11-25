import es.planidroid.app.domain.*
import java.time.*
fun main(){
 val d=LocalDate.of(2027,1,4);val z=ZoneOffset.UTC
 fun ms(h:Int)=d.atTime(h,0).toInstant(z).toEpochMilli()
 val base=PlanRequest(120,d,d,setOf(DayOfWeek.MONDAY),LocalTime.of(9,0),LocalTime.of(13,0),45,listOf(Interval(ms(10),ms(11))),z,0)
 val p=Planner.plan(base)
 check(p.missingMinutes==0);check(p.intervals.sumOf{(it.end-it.start)/60000}==120L);check(p.intervals.all{it.end<=ms(10)||it.start>=ms(11)});check(p.intervals.all{it.end-it.start<=45*60000});println("PASS 1: duración, huecos y máximo")
 check(Planner.plan(base.copy(minutes=300)).missingMinutes==120);println("PASS 2: déficit")
 check(Planner.plan(base.copy(weekdays=setOf(DayOfWeek.TUESDAY))).intervals.isEmpty());println("PASS 3: días no disponibles")
 check(Planner.plan(base.copy(minStart=ms(12))).missingMinutes==60);println("PASS 4: no programar en pasado")
 check(runCatching{Planner.plan(base.copy(deadline=d.minusDays(1)))}.isFailure);println("PASS 5: fechas inválidas")
}
