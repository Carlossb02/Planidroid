package es.planidroid.app

import es.planidroid.app.sync.GeminiGateway
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class GeminiRecoveryTest {
    @Test fun overloadUsesAnAlternateModelAndPreservesTheQuestion() = runBlocking {
        val connections=mutableListOf<FixtureConnection>()
        val statuses=mutableListOf<Boolean>()
        val gateway=GeminiGateway({"fixture"}, {statuses.add(it)}) { url->
            val code=if(connections.isEmpty())503 else 200
            FixtureConnection(url,code,answer("""{"answer":"Puedes organizar la tarde.","action":"none"}"""))
                .also{connections+=it}
        }
        val reply=gateway.assistantReply("¿Cómo organizo la tarde?","","",emptySet())
        assertEquals("Puedes organizar la tarde.",reply.answer)
        assertEquals(2,connections.size)
        assertNotEquals(connections[0].url.path,connections[1].url.path)
        assertEquals(connections[0].request.toString(),connections[1].request.toString())
        assertEquals(listOf(true),statuses)
    }

    @Test fun truncatedResponseCanRetryWithMoreOutputTokens() = runBlocking {
        val connections=mutableListOf<FixtureConnection>()
        val gateway=GeminiGateway({"fixture"},connect={url->
            val response=if(connections.isEmpty())"""{"candidates":[{"finishReason":"MAX_TOKENS"}]}"""
                else answer("""{"answer":"Dos productos","questions":[],"products":[]}""")
            FixtureConnection(url,200,response).also{connections+=it}
        })
        gateway.shoppingAdvice("¿Qué compro?","")
        val budgets=connections.map{JSONObject(it.request.toString()).getJSONObject("generationConfig").getInt("maxOutputTokens")}
        assertEquals(2,budgets.size)
        assertTrue(budgets.last()>budgets.first())
    }

    @Test fun deniedCredentialsStopWithoutRetry() = runBlocking {
        var requests=0
        val gateway=GeminiGateway({"fixture"},connect={url->requests++;FixtureConnection(url,403,"")})
        val failure=runCatching{gateway.shoppingAdvice("¿Qué compro?","")}.exceptionOrNull()
        assertEquals(1,requests)
        assertTrue(failure?.message.orEmpty().contains("denegó el acceso"))
    }

    private fun answer(value:String):String=JSONObject().put("candidates",JSONArray().put(JSONObject()
        .put("finishReason","STOP").put("content",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",value)))))).toString()

    private class FixtureConnection(url:URL,private val code:Int,private val body:String):HttpsURLConnection(url){
        val request=ByteArrayOutputStream()
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getOutputStream()=request
        override fun getInputStream()=ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getHeaderField(name:String?)=null
        override fun getCipherSuite()="fixture"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates():Array<java.security.cert.Certificate> = emptyArray()
    }
}
