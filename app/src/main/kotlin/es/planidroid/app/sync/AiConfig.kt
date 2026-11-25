package es.planidroid.app.sync
import android.content.Context
import es.planidroid.app.domain.*
import kotlinx.coroutines.tasks.await
import java.net.URL
object AiConfig {
    private fun prefs(ctx:Context,uid:String)=ctx.getSharedPreferences("ai-$uid",Context.MODE_PRIVATE)
    fun endpoint(ctx:Context,uid:String)=prefs(ctx,uid).getString("endpoint","").orEmpty()
    fun mode(ctx:Context,uid:String)=prefs(ctx,uid).getString("mode","LOCAL").orEmpty()
    fun configure(ctx:Context,uid:String,mode:String,endpoint:String){if(mode=="GEMINI"){val url=URL(endpoint);require(uid!="local"&&url.protocol=="https"&&url.host.isNotBlank()&&url.userInfo==null){"Necesitas cuenta Google y endpoint HTTPS propio"}};prefs(ctx,uid).edit().putString("mode",mode).putString("endpoint",endpoint).apply()}
    fun provider(ctx:Context,uid:String,remote:Boolean):AiProvider {if(!remote)return LocalAi();require(mode(ctx,uid)=="GEMINI"){"Activa Gemini en Ajustes"};return GeminiGateway(endpoint(ctx,uid)){val user=Cloud.auth(ctx)?.currentUser?:error("Inicia sesión");check(user.uid==uid);Cloud.requireApproval(ctx,uid);user.getIdToken(false).await().token?:error("Sin token de autenticación")}}
}
