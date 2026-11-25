package es.planidroid.app.sync
import android.content.Context
import android.content.SharedPreferences
import es.planidroid.app.domain.encode
import es.planidroid.app.domain.decode
import kotlinx.serialization.Serializable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@Serializable data class SharedSnapshot(val spaces:List<SharedSpace> = emptyList(),val invites:List<Membership> = emptyList(),val refreshed:Long=0)
@Serializable data class PendingSharedEdit(val id:String,val base:Long,val title:String,val body:String,val revision:Long)
internal class SharedCache(ctx:Context,private val uid:String){
    private val prefs=ctx.getSharedPreferences("shared-cache-$uid",Context.MODE_PRIVATE)
    companion object{private val lock=Any()}
    fun snapshot()=runCatching{decode<SharedSnapshot>(prefs.getString("snapshot","")!!)}.getOrDefault(SharedSnapshot())
    fun pending()=runCatching{decode<List<PendingSharedEdit>>(prefs.getString("pending","[]")!!)}.getOrDefault(emptyList())
    fun visible():Pair<List<SharedSpace>,List<Membership>>{
        val snap=snapshot();val pending=pending().associateBy{it.id}
        return snap.spaces.map{s->pending[s.id]?.takeIf{s.role in listOf("owner","admin","editor")}?.let{s.copy(title=it.title,body=it.body)}?:s} to snap.invites.filter{it.expiresAt>System.currentTimeMillis()}
    }
    fun observe()=callbackFlow<Pair<List<SharedSpace>,List<Membership>>>{
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,k->if(k=="pending"||k=="snapshot")trySend(visible())}
        prefs.registerOnSharedPreferenceChangeListener(listener);trySend(visible());awaitClose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    fun store(spaces:List<SharedSpace>,invites:List<Membership>)=synchronized(lock){
        check(prefs.edit().putString("snapshot",encode(SharedSnapshot(spaces,invites,System.currentTimeMillis()))).commit())
    }
    fun invalidate()=synchronized(lock){prefs.edit().remove("snapshot").commit()}
    fun enqueue(s:SharedSpace,title:String,body:String)=synchronized(lock){
        val current=pending();val old=current.find{it.id==s.id}
        val next=PendingSharedEdit(s.id,old?.base?:s.version,title,body,(old?.revision?:0)+1)
        check(prefs.edit().putString("pending",encode(current.filterNot{it.id==s.id}+next)).commit()){"No se pudo guardar el cambio"}
    }
    fun ack(edit:PendingSharedEdit,version:Long)=synchronized(lock){
        val current=pending();val snap=snapshot();val next=current.mapNotNull{p->if(p.id!=edit.id)p else if(p.revision==edit.revision)null else p.copy(base=version)}
        val visible=snap.spaces.map{s->if(s.id==edit.id)s.copy(title=edit.title,body=edit.body,version=version)else s}
        check(prefs.edit().putString("pending",encode(next)).putString("snapshot",encode(snap.copy(spaces=visible))).commit())
    }
    fun discard(id:String)=synchronized(lock){check(prefs.edit().putString("pending",encode(pending().filterNot{it.id==id})).commit())}
}
