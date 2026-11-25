package es.planidroid.app.sync

import es.planidroid.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import java.time.LocalDate

/** Client for an authenticated HTTPS proxy. The Gemini API key belongs on the server, never in this APK. */
class GeminiGateway(private val endpoint:String,private val authToken:suspend ()->String,private val status:(Boolean)->Unit={}):AiProvider {
    private suspend fun call(operation:String,text:String):JSONObject = try { request(operation,text).also{body->if(operation in listOf("summarizeTask","answerWithUserContext"))check(body.optString("answer").isNotBlank())else check(body.optJSONArray("tasks")!=null);status(true)} } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){status(false);throw e}
    private suspend fun request(operation:String,text:String):JSONObject {require(text.length<=16000);val url=URL(endpoint);require(url.protocol=="https");val token=authToken();require(token.isNotBlank());return withContext(Dispatchers.IO){val c=url.openConnection() as HttpsURLConnection;try{c.instanceFollowRedirects=false;c.requestMethod="POST";c.connectTimeout=15000;c.readTimeout=30000;c.doOutput=true;c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(JSONObject().put("operation",operation).put("text",text).toString().toByteArray(Charsets.UTF_8))};check(c.responseCode in 200..299){when(c.responseCode){401->"Sesión no válida";403->"Cuenta no autorizada para Gemini";429->"Cuota de Gemini alcanzada. Inténtalo más tarde";502->"Gemini no disponible en el servidor";else->"Gemini no disponible (HTTP ${c.responseCode})"}};JSONObject(c.inputStream.bufferedReader().use{it.readText().also{body->require(body.length<=100000)}})}finally{c.disconnect()}}}
    private suspend fun proposal(op:String,text:String):AiProposal{val p=decode<AiProposal>(call(op,text).toString());require(p.tasks.size<=30 && p.confidence in 0.0..1.0);val tasks=p.tasks.map{t->require(t.title.isNotBlank()&&t.title.length<=180&&t.minutes in 0..100000);if(t.due.isNotBlank())LocalDate.parse(t.due);Task(title=t.title,description=t.description.take(10000),category=t.category,priority=t.priority,due=t.due,minutes=t.minutes,checks=t.checks.take(100).map{Check(title=it.title.take(180))},phases=t.phases.take(30).map{Phase(title=it.title.take(180),minutes=it.minutes.coerceIn(0,100000))})};return p.copy(tasks=tasks)}
    private fun safe(t:Task)=encode(t.copy(attachments=emptyList(),contact="",phone="",groupId=""))
    override suspend fun parseNaturalLanguage(text:String)=proposal("parseNaturalLanguage",text)
    override suspend fun suggestTaskBreakdown(task:Task)=proposal("suggestTaskBreakdown",safe(task))
    override suspend fun estimateEffort(task:Task)=proposal("estimateEffort",safe(task))
    override suspend fun suggestSchedule(task:Task)=proposal("suggestSchedule",safe(task))
    override suspend fun summarizeTask(task:Task)=call("summarizeTask",safe(task)).getString("answer")
    override suspend fun answerWithUserContext(question:String,tasks:List<Task>)=call("answerWithUserContext",question+"\n"+tasks.take(10).joinToString("\n"){safe(it)}).getString("answer")
}
