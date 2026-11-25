package es.planidroid.backend

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private const val MAX_REQUEST = 40_000
private const val MAX_PROVIDER = 1_000_000
private val gson = Gson()
private val operations = setOf("parseNaturalLanguage", "suggestTaskBreakdown", "estimateEffort", "suggestSchedule", "summarizeTask", "answerWithUserContext")
private val answerOperations = setOf("summarizeTask", "answerWithUserContext")
private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
private fun HttpExchange.reply(status: Int, body: Any) {
    val bytes = gson.toJson(body).toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", "application/json; charset=utf-8")
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
private fun HttpExchange.error(status: Int, message: String) = reply(status, mapOf("error" to message))

private class GeminiClient(private val key: String, private val model: String) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    fun generate(operation: String, text: String): JsonObject {
        val answer = operation in answerOperations
        val instruction = if (answer) """Devuelve exclusivamente JSON {"answer":"respuesta en español"}. No afirmes que has modificado datos."""
        else """Devuelve exclusivamente JSON {"tasks":[{"title":"...","description":"...","minutes":0,"due":"","category":"INBOX","priority":"NONE","checks":[{"title":"..."}],"phases":[{"title":"...","minutes":0}]}],"uncertainties":[],"assumptions":[],"suggestedQuestions":[],"confidence":0.0}. category: INBOX,DEADLINE,SOON,SOMEDAY; priority: NONE,LOW,NORMAL,HIGH,URGENT; due AAAA-MM-DD o vacío; nunca inventes fecha ni duración, usa 0 si se desconoce. Máximo 30 tareas."""
        val request = HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"))
            .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json").header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to instruction))),
                "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to gson.toJson(mapOf("operation" to operation, "text" to text)))))),
                "generationConfig" to mapOf("responseMimeType" to "application/json", "maxOutputTokens" to 4096)
            )))).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val bytes = response.body().use { it.readNBytes(MAX_PROVIDER + 1) }
        require(response.statusCode() in 200..299 && bytes.size <= MAX_PROVIDER)
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        val parts = root.getAsJsonArray("candidates")[0].asJsonObject.getAsJsonObject("content").getAsJsonArray("parts")
        val raw = parts.joinToString("") { it.asJsonObject.string("text").orEmpty() }
        require(raw.isNotBlank() && raw.length <= 100_000)
        val result = JsonParser.parseString(raw).asJsonObject
        if (answer) require(result.string("answer") != null)
        else require(result.get("tasks")?.isJsonArray == true && result.getAsJsonArray("tasks").size() <= 30)
        return result
    }
}

fun main() {
    val key = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() } ?: error("Configure GEMINI_API_KEY")
    val model = System.getenv("GEMINI_MODEL")?.takeIf { Regex("gemini-[a-zA-Z0-9.-]+").matches(it) } ?: error("Configure GEMINI_MODEL")
    val allowed = System.getenv("PLANIDROID_ALLOWED_UIDS").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    require(allowed.isNotEmpty()) { "Configure PLANIDROID_ALLOWED_UIDS" }
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    require(port in 1..65535)
    val options = FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault()).apply {
        System.getenv("GOOGLE_CLOUD_PROJECT")?.takeIf { it.isNotBlank() }?.let { setProjectId(it) }
    }.build()
    FirebaseApp.initializeApp(options)
    val auth = FirebaseAuth.getInstance()
    val quota = RateLimiter()
    val gemini = GeminiClient(key, model)
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 32)
    // Bounded workers; use a trusted HTTPS reverse proxy with ingress limits and timeouts.
    val executor = ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(32), ThreadPoolExecutor.AbortPolicy())
    server.executor = executor
    server.createContext("/ai") { exchange ->
        try {
            if (exchange.requestMethod != "POST" || exchange.requestURI.path != "/ai") { exchange.error(404, "Not found"); return@createContext }
            val token = exchange.requestHeaders.getFirst("Authorization")?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() && it.length <= 8192 }
            if (token == null) { exchange.error(401, "Authentication required"); return@createContext }
            val uid = try { auth.verifyIdToken(token, true).uid } catch (_: Exception) { exchange.error(401, "Invalid token"); return@createContext }
            if (uid !in allowed) { exchange.error(403, "Not allowed"); return@createContext }
            val bytes = exchange.requestBody.use { it.readNBytes(MAX_REQUEST + 1) }
            if (bytes.size > MAX_REQUEST) { exchange.error(413, "Too large"); return@createContext }
            val input = try { JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject } catch (_: Exception) { exchange.error(400, "Invalid JSON"); return@createContext }
            val operation = input.string("operation")
            val text = input.string("text")
            if (operation == null || operation !in operations || text == null || text.isBlank() || text.length > 16_000) { exchange.error(400, "Invalid request"); return@createContext }
            if (!quota.allow(uid)) { exchange.error(429, "Hourly limit"); return@createContext }
            val result = try { gemini.generate(operation, text) } catch (_: Exception) { exchange.error(502, "Provider unavailable or invalid response"); return@createContext }
            exchange.reply(200, result)
        } catch (_: Exception) {
            runCatching { exchange.error(500, "Request failed") }
        } finally { exchange.close() }
    }
    Runtime.getRuntime().addShutdownHook(Thread { server.stop(2); executor.shutdown() })
    server.start()
}
