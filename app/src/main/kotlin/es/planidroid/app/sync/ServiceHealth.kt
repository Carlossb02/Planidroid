package es.planidroid.app.sync
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
object ServiceHealth {
    private fun prefs(ctx:Context,uid:String)=ctx.getSharedPreferences("service-health-$uid",Context.MODE_PRIVATE)
    fun mark(ctx:Context,uid:String,service:String,ok:Boolean){prefs(ctx,uid).edit().putBoolean(service,ok).putLong(service+"-at",System.currentTimeMillis()).apply()}
    fun observe(ctx:Context,uid:String,service:String)=callbackFlow<Boolean>{
        val p=prefs(ctx,uid)
        fun value()=p.getBoolean(service,false)&&System.currentTimeMillis()-p.getLong(service+"-at",0) in 0..86_400_000L
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key==service||key==service+"-at")trySend(value())}
        p.registerOnSharedPreferenceChangeListener(listener);trySend(value());awaitClose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
}
