package es.planidroid.app.sync

internal enum class GeminiRecovery { NONE, RETRY, ALTERNATE, MORE_TOKENS, COMPATIBILITY }

internal data class GeminiHttpFailure(
    val message:String,
    val recovery:GeminiRecovery=GeminiRecovery.NONE,
    val waitMillis:Long?=null
)

internal fun geminiHttpFailure(code:Int,retryAfter:String?):GeminiHttpFailure = when(code){
    400 -> GeminiHttpFailure("Gemini rechazó la solicitud. Comprueba la configuración del proyecto en AI Studio.",GeminiRecovery.COMPATIBILITY)
    401,403 -> GeminiHttpFailure("Gemini denegó el acceso. Revisa la clave y los permisos en AI Studio.")
    404 -> GeminiHttpFailure("El modelo de Gemini no está disponible para tu proyecto.",GeminiRecovery.ALTERNATE)
    429 -> {
        val seconds=retryAfter?.trim()?.toLongOrNull()?.takeIf{it in 1..4}
        GeminiHttpFailure("Gemini alcanzó el límite de tu proyecto. Revisa la cuota en AI Studio o inténtalo más tarde.",
            if(seconds==null)GeminiRecovery.NONE else GeminiRecovery.RETRY,seconds?.times(1000))
    }
    503 -> GeminiHttpFailure("Gemini está temporalmente saturado. Vuelve a intentarlo en unos minutos.",GeminiRecovery.ALTERNATE)
    408,500,502,504 -> GeminiHttpFailure("Gemini no respondió a tiempo. Vuelve a intentarlo en unos minutos.",GeminiRecovery.RETRY)
    else -> GeminiHttpFailure("Gemini no está disponible (HTTP "+code+").")
}
