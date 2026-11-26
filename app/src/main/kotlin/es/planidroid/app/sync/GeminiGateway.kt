package es.planidroid.app.sync

import es.planidroid.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** BYOK: send key only in HTTPS header to the fixed official Google endpoint. Never log provider bodies. */
class GeminiGateway(private val apiKey:suspend ()->String,private val status:(Boolean)->Unit={}):AiProvider {
    companion object {const val MODEL="gemini-3.5-flash-lite"}
    private class GeminiFailure(message:String, val retry:Boolean=false, val fallback:Boolean=false, val expand:Boolean=false) : IllegalStateException(message)

    private suspend fun call(
        instruction:String, text:String, image:ByteArray?=null, maxTokens:Int=2048,
        schema:JSONObject?=null
    ):JSONObject {
        require(text.length<=16000 && (image==null || image.size<=2_000_000))
        try {
            val key=apiKey()
            val parts=JSONArray().put(JSONObject().put("text",text))
            if(image!=null)parts.put(JSONObject().put("inlineData",JSONObject().put("mimeType","image/jpeg")
                .put("data",android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP))))
            val config=JSONObject().put("responseMimeType","application/json")
                .put("maxOutputTokens",maxTokens).put("candidateCount",1)
                .put("thinkingConfig",JSONObject().put("thinkingLevel","MINIMAL"))
            if(schema!=null)config.put("responseJsonSchema",schema)
            val request=JSONObject()
                .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",instruction))))
                .put("contents",JSONArray().put(JSONObject().put("parts",parts)))
                .put("generationConfig",config)
            val result=try {
                generate(key,MODEL,request)
            } catch (failure:GeminiFailure) {
                if(!failure.retry && !failure.fallback)throw failure
                delay(800L + kotlin.random.Random.nextLong(400L))
                if(failure.expand)config.put("maxOutputTokens", (maxTokens*2).coerceAtMost(8192))
                generate(key,if(failure.fallback)"gemini-3.8-flash" else MODEL,request)
            }
            status(true)
            return result
        } catch (exception:CancellationException) {
            throw exception
        } catch (exception:Exception) {
            status(false)
            val message=when(exception){
                is GeminiFailure, is Cloud.ApprovalRequired -> exception.message
                is java.io.IOException -> "No se pudo conectar con Gemini. Comprueba tu conexión y vuelve a intentarlo."
                else -> "Gemini devolvió una respuesta que no se pudo leer. Vuelve a intentarlo."
            }
            throw IllegalStateException(message)
        }
    }

    private suspend fun generate(key:String,model:String,request:JSONObject):JSONObject=withContext(Dispatchers.IO){
        val connection=URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.requestMethod="POST"
            connection.connectTimeout=10_000
            connection.readTimeout=25_000
            connection.doOutput=true
            connection.setRequestProperty("x-goog-api-key",key)
            connection.setRequestProperty("Content-Type","application/json")
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            when(val code=connection.responseCode){
                in 200..299 -> Unit
                400 -> throw GeminiFailure("Gemini rechazó la solicitud. Comprueba que tu clave y proyecto están activos en AI Studio.")
                401,403 -> throw GeminiFailure("Gemini denegó el acceso. Revisa la clave y los permisos en AI Studio.")
                404 -> throw GeminiFailure("El modelo de Gemini no está disponible para tu proyecto.",fallback=true)
                429 -> throw GeminiFailure("Gemini alcanzó el límite de tu proyecto. Revisa la cuota en AI Studio o inténtalo más tarde.")
                500,502,503,504 -> throw GeminiFailure("Gemini está temporalmente saturado. Vuelve a intentarlo en unos minutos.",retry=code!=503,fallback=code==503)
                else -> throw GeminiFailure("Gemini no está disponible (HTTP "+code+").")
            }
            val body=connection.inputStream.use { stream ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true){
                    val length=stream.read(buffer)
                    if(length<0)break
                    require(output.size()+length<=1_000_000){"Respuesta demasiado grande"}
                    output.write(buffer,0,length)
                }
                output.toString("UTF-8")
            }
            val response=JSONObject(body)
            val candidate=response.optJSONArray("candidates")?.optJSONObject(0)
                ?:throw GeminiFailure(if(response.optJSONObject("promptFeedback")?.optString("blockReason").isNullOrBlank())
                    "Gemini no devolvió ninguna respuesta." else "Gemini bloqueó esta consulta. Prueba a reformularla.")
            when(candidate.optString("finishReason")){
                "MAX_TOKENS" -> throw GeminiFailure("La respuesta de Gemini era demasiado larga. Divide la petición.",retry=true,expand=true)
                "SAFETY","BLOCKLIST","PROHIBITED_CONTENT","RECITATION","SPII" ->
                    throw GeminiFailure("Gemini bloqueó esta respuesta. Prueba a reformular la consulta.")
            }
            val output=candidate.getJSONObject("content").getJSONArray("parts")
            val raw=(0 until output.length()).map { output.getJSONObject(it) }
                .filterNot { it.optBoolean("thought") }.joinToString("") { it.optString("text") }.trim()
            if(raw.length !in 2..100_000)throw GeminiFailure("Gemini devolvió una respuesta vacía o inválida.",retry=true)
            try {
                JSONObject(raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            } catch (invalid:JSONException) {
                throw GeminiFailure("Gemini devolvió un formato inválido. Vuelve a intentarlo.",retry=true)
            }
        } catch (timeout:java.net.SocketTimeoutException) {
            throw GeminiFailure("Gemini tardó demasiado en responder. Vuelve a intentarlo.",retry=true)
        } finally {
            connection.disconnect()
        }
    }

    private val taskInstruction="""JSON compacto, omite campos vacíos y valores por defecto; tasks (máximo 15), confidence; clarifications [{taskIndex,field,question}] máximo 2, solo datos imprescindibles no inferibles. field topicCount,minutes (minutos),due (AAAA-MM-DD),window (HH:mm a HH:mm),title o detail. Siempre devuelve una tarea provisional utilizable; no preguntes por preferencias opcionales ni por datos ya escritos. Cada tarea: title, description, startDate, reminder, reminderTime, reminderBeforeDays, goal, minutes (total, 0 si desconocido), due (AAAA-MM-DD o vacío), category INBOX/DEADLINE/SOON/SOMEDAY, priority NONE/LOW/NORMAL/HIGH/URGENT, checks [{title}], phases [{title,minutes}]. Incluye planHints {windowStart,windowEnd (HH:mm, puede cruzar medianoche: 21:00 a 06:00), availableWeekdays (1 lunes a 7 domingo), excludedWeekdays, daysPerWeek, dailyMinutes, minutesPerTopic, topicCount, sessionLimit (normalmente 90-150 min, no 60 fijo), breakMinutes (recomienda descansos según intensidad y duración de fases; 0 cuando no corresponda), breakReason (explica por qué sugieres esas pausas), compact (true si prefiere bloques largos), finishBeforeDeadline, explanation}. Respeta el horario explícito, no sustituyas noches o madrugadas por mañanas; si es ambiguo indica la suposición. Conserva disponibilidad y duración por tema en descripción; no inventes fechas ni número de temas. Sugiere descansos y bloques junto con fases en esta misma respuesta; breakReason máximo 100 caracteres. No ejecutes acciones."""
    private val proposalJson=kotlinx.serialization.json.Json{ignoreUnknownKeys=true;coerceInputValues=true}
    private suspend fun proposal(op:String,text:String):AiProposal{
        val raw=call(taskInstruction,"Hoy: "+LocalDate.now()+"\n"+op+"\n"+text.take(12000),maxTokens=3072)
        val p=try{
            proposalJson.decodeFromString(AiProposal.serializer(),raw.toString())
        }catch(invalid:kotlinx.serialization.SerializationException){
            throw IllegalStateException("Gemini devolvió una propuesta incompleta. Prueba a separar la petición en tareas más pequeñas.")
        }
        check(p.tasks.isNotEmpty()&&p.tasks.all{it.title.isNotBlank()}){"Gemini no pudo identificar las tareas. Indica qué quieres hacer."}
        val tasks=p.tasks.take(15).map { task ->
            val due=runCatching{LocalDate.parse(task.due)}.getOrNull()
            val start=runCatching{LocalDate.parse(task.startDate)}.getOrNull()?.takeIf{due==null||it<=due}
            Task(
                title=task.title.trim().take(180),
                description=task.description.take(10000),
                category=if(due==null&&task.category==Category.DEADLINE)Category.INBOX else task.category,
                priority=task.priority,
                due=due?.toString().orEmpty(),
                startDate=start?.toString().orEmpty(),
                reminder=task.reminder&&due!=null,
                reminderTime=runCatching{java.time.LocalTime.parse(task.reminderTime).toString()}.getOrDefault("09:00"),
                reminderBeforeDays=task.reminderBeforeDays.coerceIn(0,365),
                goal=task.goal.take(1000),
                minutes=task.minutes.coerceIn(0,100000),
                checks=task.checks.filter{it.title.isNotBlank()}.take(100).map{Check(title=it.title.take(180))},
                phases=task.phases.filter{it.title.isNotBlank()}.take(30).map{Phase(title=it.title.take(180),minutes=it.minutes.coerceIn(0,100000))},
                planHints=PlanningPreferences.validated(task.planHints)
            )
        }
        return p.copy(tasks=tasks,confidence=p.confidence.takeIf{it.isFinite()}?.coerceIn(0.0,1.0)?:0.0,clarifications=CaptureClarifications.questions(p).filter{it.taskIndex in tasks.indices}.map{it.copy(question=it.question.take(180))},suggestedQuestions=p.suggestedQuestions.take(2),assumptions=p.assumptions.take(2),uncertainties=p.uncertainties.take(2))
    }
    private fun safe(t:Task)=JSONObject().put("title",t.title.take(180)).put("description",t.description.take(1200)).put("due",t.due).put("minutes",t.minutes).put("phases",JSONArray(t.phases.take(20).map{JSONObject().put("title",it.title.take(100)).put("minutes",it.minutes)})).put("planHints",JSONObject(kotlinx.serialization.json.Json{encodeDefaults=false}.encodeToString(PlanHints.serializer(),t.planHints))).toString()
    suspend fun planWithContext(task:Task,request:String,availability:String)=proposal("Revisar tarea, fases, horarios y descansos; conservar preferencias no modificadas",safe(task)+"\n"+request.take(1200)+"\n"+availability.take(6000))
    override suspend fun parseNaturalLanguage(text:String)=proposal("Interpretar tareas",text)
    override suspend fun suggestTaskBreakdown(task:Task)=proposal("Desglosar",safe(task))
    override suspend fun estimateEffort(task:Task)=proposal("Estimar esfuerzo",safe(task))
    override suspend fun suggestSchedule(task:Task)=proposal("Proponer planificación y fases",safe(task))
    override suspend fun summarizeTask(task:Task)=call("""Devuelve JSON {"answer":"resumen en español"}.""",safe(task),maxTokens=384).getString("answer")
    override suspend fun answerWithUserContext(question:String,tasks:List<Task>)=call("""Devuelve JSON {"answer":"respuesta en español"}. No afirmes haber modificado datos.""",(question+"\n"+tasks.take(10).joinToString("\n"){safe(it)}).take(8000),maxTokens=768).getString("answer")
    suspend fun agendaAdvice(context:String)=call("""Devuelve JSON {"answer":"recomendación concreta en español","taskIds":["id exacto de tarea"],"restPolicies":[{"taskId":"id exacto","focusMinutes":120,"breakMinutes":15,"reason":"justificación de la pausa"}]}. Analiza horas disponibles, vencimientos y prioridad. Solo sugiere reorganizar tareas marcadas como movibles, nunca citas externas ni completadas; no inventes IDs, horarios libres ni permisos. Para cada tarea de estudio seleccionada sugiere foco y descanso según fases y citas; focusMinutes 15 a 240, breakMinutes 0 a 60. Explica por qué y qué sacrificar si no cabe todo. No afirmes que has guardado cambios.""",context.take(9000),maxTokens=2048)
    suspend fun shoppingAdvice(request:String,ingredients:String,clarified:Boolean=false)=call(
        """Responde en español a la pregunta del usuario con consejos concretos. Añade productos solo si ayudan a la consulta, sin inventar precios, disponibilidad ni enlaces. Usa cantidades métricas y unidades habituales en España. Si falta un dato imprescindible, haz hasta dos preguntas juntas. Tras una aclaración, ofrece una respuesta con supuestos razonables. No afirmes haber comprado nada ni cambiado la lista.""",
        request.take(3600)+"\nLista compartida por el usuario: "+ingredients.take(2800)+
            if(clarified)"\nResponde con lo que ya sabes, sin hacer más preguntas."else "",
        maxTokens=2600,
        schema=JSONObject("""{"type":"object","properties":{"answer":{"type":"string"},"questions":{"type":"array","items":{"type":"string"},"maxItems":2},"products":{"type":"array","maxItems":20,"items":{"type":"object","properties":{"name":{"type":"string"},"quantity":{"type":["number","null"]},"unit":{"type":"string"},"reason":{"type":"string"}},"required":["name"]}}},"required":["answer","questions","products"]}""")
    )
    suspend fun recipe(ingredients:String,request:String)=call("""Devuelve exclusivamente JSON {"recipes":[{"title":"nombre concreto","servings":2,"ingredients":[{"name":"arroz","quantity":160,"unit":"g"}],"steps":["paso con tiempos, temperatura y técnica"]}]}. Propón hasta 3 recetas según petición. Incluye TODOS los ingredientes con cantidad positiva y unidad española: g, kg, ml, L, ud, diente, cucharadita, cucharada, pizca, manojo, rebanada, loncha, vaso o taza. No uses unidades imperiales. Especifica raciones y pasos precisos. No confundas la lista de compra con existencias. Respeta restricciones del usuario. No inventes existencias ni añadas productos no alimentarios.""",(request+"\nProductos de la lista y cantidades: "+ingredients).take(6000),maxTokens=2048)
    suspend fun recipePhoto(image:ByteArray)=call("""Transcribe la receta visible en la foto, sin inventar ingredientes, cantidades, raciones o pasos. Devuelve JSON {"recipes":[{"title":"título","servings":0,"ingredients":[{"name":"ingrediente","quantity":0,"unit":""}],"steps":["paso"]}]}. Usa 0 para cantidad o raciones ilegibles/desconocidas, unidad vacía si no consta. Unidades métricas y formatos culinarios españoles. Conserva los pasos visibles. Si no hay receta legible, recipes vacío. Máximo 3 recetas.""","Transcribe el recetario",image,maxTokens=2400)
    suspend fun shoppingText(text:String,image:ByteArray?=null)=call("""Transcribe una lista de compra. Devuelve JSON {"products":["producto por línea con cantidad si consta"]}. No inventes productos ni cantidades, omite texto tachado si es inequívoco. Máximo 100.""",text.take(2000),image,maxTokens=1536).getJSONArray("products").let{a->(0 until minOf(100,a.length())).joinToString("\n"){a.getString(it).take(180)}}
}
