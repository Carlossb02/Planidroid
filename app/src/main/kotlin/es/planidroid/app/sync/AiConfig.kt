package es.planidroid.app.sync
import android.content.Context
import es.planidroid.app.domain.*
object AiConfig {
    fun configured(ctx:Context,uid:String)=PersonalGeminiKey.configured(ctx,uid)
    fun provider(ctx:Context,uid:String):GeminiGateway {
        require(uid!="local"&&Cloud.auth(ctx)?.currentUser?.uid==uid){"Inicia sesión con Google"}
        check(configured(ctx,uid)){"Conecta tu clave personal de Gemini en Ajustes. Puedes seguir sin IA."}
        return GeminiGateway({
            check(Cloud.auth(ctx)?.currentUser?.uid==uid){"La cuenta ha cambiado"}
            Cloud.requireApproval(ctx,uid)
            PersonalGeminiKey.load(ctx,uid)
        },{ok->ServiceHealth.mark(ctx,uid,"gemini",ok)})
    }
}
