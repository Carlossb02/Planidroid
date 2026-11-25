package es.planidroid.app.domain
import kotlinx.serialization.Serializable
@Serializable data class ExternalEvent(val id:String,val title:String,val start:Long,val end:Long,val busy:Boolean=true)
@Serializable data class CalendarStamp(val start:Long,val end:Long,val title:String)
@Serializable data class CalendarLink(val id:String,val stamp:CalendarStamp,val etag:String)
enum class Reconcile { SAME, PUSH, PULL, CONFLICT }
object CalendarMerge {
    fun decide(base:CalendarStamp?,local:CalendarStamp,remote:CalendarStamp):Reconcile=when{
        local==remote->Reconcile.SAME
        base==null->Reconcile.CONFLICT
        remote==base->Reconcile.PUSH
        local==base->Reconcile.PULL
        else->Reconcile.CONFLICT
    }
}
