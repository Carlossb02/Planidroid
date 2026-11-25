package es.planidroid.app.sync
import android.content.Context
import es.planidroid.app.BuildConfig
import es.planidroid.app.domain.*
import kotlinx.coroutines.tasks.await
import java.net.URI
object AiConfig {
    fun configured()=BuildConfig.GEMINI_ENDPOINT.isNotBlank()
    fun provider(ctx:Context,uid:String):AiProvider {
        require(uid!="local"){"Inicia sesión con Google para usar Gemini"}
        check(configured()){"Gemini todavía no está configurado por el administrador"}
        val endpoint=URI(BuildConfig.GEMINI_ENDPOINT)
        require(endpoint.scheme=="https"&&endpoint.host!=null&&endpoint.userInfo==null&&endpoint.query==null&&endpoint.fragment==null&&endpoint.path=="/ai")
        return GeminiGateway(endpoint.toString(),{
            val user=Cloud.auth(ctx)?.currentUser?:error("Inicia sesión")
            check(user.uid==uid){"La cuenta ha cambiado"}
            Cloud.requireApproval(ctx,uid)
            user.getIdToken(false).await().token?:error("Sin token de autenticación")
        },{ok->ServiceHealth.mark(ctx,uid,"gemini",ok)})
    }
}
