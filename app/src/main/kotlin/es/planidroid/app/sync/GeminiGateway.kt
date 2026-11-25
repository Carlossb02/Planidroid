package es.planidroid.app.sync

import es.planidroid.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** BYOK: send key only in HTTPS header to the fixed official Google endpoint. Never log provider bodies. */
class GeminiGateway(private val apiKey:suspend ()->String,private val status:(Boolean)->Unit={}):AiProvider {
    companion object {const val MODEL="gemini-3.5-flash-lite"}
    private suspend fun call(instruction:String,text:String,image:ByteArray?=null):JSONObject=try{
        require(text.length<=16000);require(image==null||image.size<=2_000_000)
        val key=apiKey()
        val body=withContext(Dispatchers.IO){
            val parts=JSONArray().put(JSONObject().put("text",text))
            if(image!=null)parts.put(JSONObject().put("inlineData",JSONObject().put("mimeType","image/jpeg").put("data",android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP))))
            val request=JSONObject().put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",instruction))))
                .put("contents",JSONArray().put(JSONObject().put("parts",parts)))
                .put("generationConfig",JSONObject().put("responseMimeType","application/json").put("maxOutputTokens",4096))
            val c=URL("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent").openConnection() as HttpsURLConnection
            try{
                c.instanceFollowRedirects=false;c.requestMethod="POST";c.connectTimeout=15000;c.readTimeout=40000;c.doOutput=true
                c.setRequestProperty("x-goog-api-key",key);c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use{it.write(request.toString().toByteArray(Charsets.UTF_8))}
                val code=c.responseCode
                check(code in 200..299){when(code){400,401,403->"Gemini rechazó la clave o el proyecto. Comprueba AI Studio.";404->"Modelo Gemini no disponible en tu proyecto";429->"Cuota personal de Gemini alcanzada";else->"Gemini no disponible (HTTP "+code+")"}}
                val bytes=c.inputStream.use{stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=1_000_000){"Respuesta demasiado grande"};out.write(buffer,0,n)};out.toByteArray()}
                val response=JSONObject(bytes.toString(Charsets.UTF_8))
                val candidate=response.optJSONArray("candidates")?.optJSONObject(0)?:error("Gemini no devolvió una respuesta")
                val output=candidate.getJSONObject("content").getJSONArray("parts")
                val raw=(0 until output.length()).map{output.getJSONObject(it)}.filterNot{it.optBoolean("thought")}.joinToString(""){it.optString("text")}
                require(raw.length in 2..100_000){"Respuesta inválida"}
                JSONObject(raw)
            }finally{c.disconnect()}
        }
        status(true);body
    }catch(e:CancellationException){throw e}catch(e:Exception){status(false);throw IllegalStateException(if(e is IllegalStateException&&e.message?.startsWith("Gemini")==true)e.message else if(e is Cloud.ApprovalRequired)e.message else "Gemini no disponible. Comprueba la conexión, clave y autorización.",null)}
    private val taskInstruction="""Devuelve JSON con tasks (máximo 30), uncertainties, assumptions, suggestedQuestions, confidence. Cada tarea: title, description, minutes (total, 0 si desconocido), due (AAAA-MM-DD o vacío), category INBOX/DEADLINE/SOON/SOMEDAY, priority NONE/LOW/NORMAL/HIGH/URGENT, checks [{title}], phases [{title,minutes}]. Incluye planHints {windowStart,windowEnd (HH:mm, puede cruzar medianoche: 21:00 a 06:00), availableWeekdays (1 lunes a 7 domingo), excludedWeekdays, daysPerWeek, dailyMinutes, minutesPerTopic, topicCount, sessionLimit (normalmente 90-150 min, no 60 fijo), breakMinutes (0 sin pausas, normalmente 10-20), compact (true si prefiere bloques largos), finishBeforeDeadline, explanation}. Respeta el horario explícito, no sustituyas noches o madrugadas por mañanas; si es ambiguo indica la suposición. Conserva disponibilidad y duración por tema en descripción; no inventes fechas ni número de temas. No ejecutes acciones."""
    private suspend fun proposal(op:String,text:String):AiProposal{
        val raw=call(taskInstruction,"Fecha de hoy: "+LocalDate.now()+"\nOperación: "+op+"\n"+text)
        val p=kotlinx.serialization.json.Json{ignoreUnknownKeys=true}.decodeFromString(AiProposal.serializer(),raw.toString())
        require(p.tasks.size<=30&&p.confidence in 0.0..1.0)
        val tasks=p.tasks.map{t->require(t.title.isNotBlank()&&t.title.length<=180&&t.minutes in 0..100000);if(t.due.isNotBlank())LocalDate.parse(t.due);Task(title=t.title,description=t.description.take(10000),category=t.category,priority=t.priority,due=t.due,minutes=t.minutes,checks=t.checks.take(100).map{Check(title=it.title.take(180))},phases=t.phases.take(30).map{Phase(title=it.title.take(180),minutes=it.minutes.coerceIn(0,100000))},planHints=PlanningPreferences.validated(t.planHints))}
        return p.copy(tasks=tasks)
    }
    private fun safe(t:Task)=encode(t.copy(attachments=emptyList(),contact="",phone="",groupId=""))
    override suspend fun parseNaturalLanguage(text:String)=proposal("Interpretar tareas",text)
    override suspend fun suggestTaskBreakdown(task:Task)=proposal("Desglosar",safe(task))
    override suspend fun estimateEffort(task:Task)=proposal("Estimar esfuerzo",safe(task))
    override suspend fun suggestSchedule(task:Task)=proposal("Proponer planificación y fases",safe(task))
    override suspend fun summarizeTask(task:Task)=call("""Devuelve JSON {"answer":"resumen en español"}.""",safe(task)).getString("answer")
    override suspend fun answerWithUserContext(question:String,tasks:List<Task>)=call("""Devuelve JSON {"answer":"respuesta en español"}. No afirmes haber modificado datos.""",(question+"\n"+tasks.take(10).joinToString("\n"){safe(it)}).take(16000)).getString("answer")
    suspend fun agendaAdvice(context:String)=call("""Devuelve JSON {"answer":"recomendación concreta en español","taskIds":["id exacto de tarea"]}. Analiza horas disponibles, vencimientos y prioridad. Solo sugiere reorganizar tareas marcadas como movibles, nunca citas externas ni completadas; no inventes IDs, horarios libres ni permisos. Explica por qué y qué sacrificar si no cabe todo. No afirmes que has guardado cambios.""",context.take(16000))
    suspend fun recipe(ingredients:String,request:String)=call("""Devuelve JSON {"answer":"recetas, cantidades y pasos en español","products":["producto por línea"]}. Los productos son solo ingredientes faltantes sugeridos, no inventes existencias. No incluyas productos no alimentarios.""",(request+"\nProductos de la lista o disponibles: "+ingredients).take(16000))
    suspend fun shoppingText(text:String,image:ByteArray?=null)=call("""Transcribe una lista de compra. Devuelve JSON {"products":["producto por línea con cantidad si consta"]}. No inventes productos ni cantidades, omite texto tachado si es inequívoco. Máximo 100.""",text,image).getJSONArray("products").let{a->(0 until minOf(100,a.length())).joinToString("\n"){a.getString(it).take(180)}}
}
