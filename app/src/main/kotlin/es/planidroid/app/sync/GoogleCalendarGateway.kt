package es.planidroid.app.sync

import es.planidroid.app.domain.Session
import java.net.URL
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.time.Instant
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** OAuth access tokens are supplied in memory by a future, separately consented authorization flow. */
class CalendarHttpException(val status:Int):Exception("Calendar HTTP $status")
class GoogleCalendarGateway(private val accessToken:suspend ()->String){
    private fun segment(s:String)=URLEncoder.encode(s,"UTF-8").replace("+","%20")
    private suspend fun request(method:String,path:String,body:JSONObject?=null,etag:String?=null):JSONObject {
        val token=accessToken();require(token.isNotBlank())
        return withContext(Dispatchers.IO){val c=URL("https://www.googleapis.com/calendar/v3/$path").openConnection() as HttpURLConnection
            try{c.instanceFollowRedirects=false;c.requestMethod=method;c.connectTimeout=15000;c.readTimeout=20000;c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Accept","application/json");if(etag!=null)c.setRequestProperty("If-Match",etag);if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}};val code=c.responseCode;if(code !in 200..299)throw CalendarHttpException(code);if(code==204)JSONObject() else JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}}
    }
    suspend fun list(calendarId:String,from:Instant,to:Instant):List<JSONObject>{val out=mutableListOf<JSONObject>();var next="";do{val p=request("GET","calendars/${segment(calendarId)}/events?singleEvents=true&showDeleted=false&maxResults=250&timeMin=${segment(from.toString())}&timeMax=${segment(to.toString())}"+(if(next.isEmpty())"" else "&pageToken=${segment(next)}"));val items=p.optJSONArray("items");if(items!=null)for(i in 0 until items.length())out+=items.getJSONObject(i);next=p.optString("nextPageToken")}while(next.isNotEmpty());return out}
    fun eventId(s:Session):String="p"+s.id.replace("-","").lowercase().also{require(it.matches(Regex("[a-v0-9]{5,100}")))}
    private fun event(s:Session,title:String)=JSONObject().put("summary",title).put("start",JSONObject().put("dateTime",Instant.ofEpochMilli(s.start).toString())).put("end",JSONObject().put("dateTime",Instant.ofEpochMilli(s.end).toString())).put("extendedProperties",JSONObject().put("private",JSONObject().put("planidroidSessionId",s.id)))
    suspend fun insert(calendarId:String,s:Session,title:String)=request("POST","calendars/${segment(calendarId)}/events",event(s,title).put("id",eventId(s)))
    suspend fun get(calendarId:String,eventId:String)=request("GET","calendars/${segment(calendarId)}/events/${segment(eventId)}")
    suspend fun updateOwned(calendarId:String,s:Session,title:String,expectedEtag:String):JSONObject{val existing=get(calendarId,eventId(s));check(existing.optString("etag")==expectedEtag);check(existing.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("planidroidSessionId")==s.id);existing.put("summary",title).put("start",event(s,title).getJSONObject("start")).put("end",event(s,title).getJSONObject("end"));return request("PUT","calendars/${segment(calendarId)}/events/${segment(eventId(s))}",existing,expectedEtag)}
    suspend fun deleteOwned(calendarId:String,s:Session,expectedEtag:String){val existing=get(calendarId,eventId(s));check(existing.optString("etag")==expectedEtag);check(existing.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("planidroidSessionId")==s.id);request("DELETE","calendars/${segment(calendarId)}/events/${segment(eventId(s))}",etag=expectedEtag)}
}
enum class CalendarSyncMode { OFF, MANUAL, AUTOMATIC_ALL }
