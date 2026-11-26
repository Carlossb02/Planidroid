package es.planidroid.app.ui
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.*

internal fun safeShoppingReference(url:String):Boolean=runCatching{val u=java.net.URI(url);u.scheme=="https"&&u.host?.isNotBlank()==true&&u.userInfo==null&&url.length<=2048}.getOrDefault(false)
internal fun openShoppingReference(ctx:Context,url:String){if(safeShoppingReference(url))runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
@Composable internal fun ShoppingAiActions(recipes:()->Unit,assistant:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilledTonalButton(enabled=enabled,onClick=recipes,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)){Icon(Icons.Default.Restaurant,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Recetas")}
        FilledTonalButton(enabled=enabled,onClick=assistant,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)){Icon(Icons.Default.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Gemini")}
    }
}
private data class SuggestedProduct(val product: Shopping, val reason: String)

@Composable
fun ShoppingAssistant(
    uid: String, current: List<Shopping>, listId: String,
    close: () -> Unit, add: (List<Shopping>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var request by rememberSaveable { mutableStateOf("") }
    var previousRequest by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf(emptyList<String>()) }
    var answers by remember { mutableStateOf(emptyMap<Int, String>()) }
    var answer by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(emptyList<SuggestedProduct>()) }
    var chosen by remember { mutableStateOf(emptySet<Int>()) }
    var quantities by remember { mutableStateOf(emptyMap<Int, String>()) }
    var units by remember { mutableStateOf(emptyMap<Int, String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var connect by remember { mutableStateOf(false) }
    var includeList by remember { mutableStateOf(false) }

    fun ask(followUp: Boolean = false) {
        if (busy || request.isBlank()) return
        if (!AiConfig.configured(context, uid)) { connect = true; return }
        val query = if (followUp) previousRequest + "\n" +
            questions.mapIndexed { index, question -> question + ": " + answers[index].orEmpty() }.joinToString("\n")
        else request.trim()
        busy = true
        error = ""
        scope.launch {
            try {
                val listContext = if (includeList) current.take(40).joinToString("; ") {
                    it.name + if (it.quantitySpecified) " " + amountText(it.quantity) + " " + it.unit else ""
                } else ""
                val response = AiConfig.provider(context, uid).shoppingAdvice(query, listContext, followUp)
                val nextQuestions = (if (followUp) null else response.optJSONArray("questions"))?.let { rows ->
                    (0 until minOf(rows.length(), 2)).map { rows.optString(it, "").take(240) }.filter { it.isNotBlank() }
                }.orEmpty()
                val nextAnswer = response.optString("answer", "").take(5000)
                val nextProducts = response.optJSONArray("products")?.let { rows ->
                    (0 until minOf(rows.length(), 20)).mapNotNull { index ->
                        val value = rows.optJSONObject(index) ?: return@mapNotNull null
                        val name = value.optString("name", "").trim().take(180)
                        if (name.isBlank()) return@mapNotNull null
                        val quantity = value.optDouble("quantity").takeIf { it.isFinite() && it > 0 && it <= 100000 }
                        val unit = SpanishUnits.canonical(value.optString("unit", "")).orEmpty()
                        SuggestedProduct(Shopping(name=name, quantity=quantity?:1.0, unit=unit, listId=listId,
                            quantitySpecified=quantity!=null, icon=guessIcon(name)), value.optString("reason", "").take(200))
                    }
                }.orEmpty()
                check(nextAnswer.isNotBlank() || nextQuestions.isNotEmpty() || nextProducts.isNotEmpty()) {
                    "Gemini no devolvió una respuesta útil. Puedes volver a intentarlo."
                }
                if (!followUp) previousRequest = query
                questions = nextQuestions
                answers = emptyMap()
                answer = nextAnswer
                products = nextProducts
                chosen = emptySet()
                quantities = nextProducts.mapIndexed { index, item ->
                    index to if (item.product.quantitySpecified) amountText(item.product.quantity) else ""
                }.toMap()
                units = nextProducts.mapIndexed { index, item -> index to item.product.unit }.toMap()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.localizedMessage ?: "No se pudo consultar Gemini"
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest=close, properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal=4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=close) { Icon(Icons.Default.Close, "Cerrar") }
                    Text("Preguntar a Gemini", style=MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(request, { request=it.take(2000) }, Modifier.fillMaxWidth(),
                        label={ Text("¿En qué te ayudo?") }, minLines=3, enabled=!busy)
                    if (current.isNotEmpty()) FilterChip(includeList, { includeList=!includeList },
                        label={ Text("Incluir mi lista en la consulta") }, enabled=!busy)
                    Button(enabled=!busy && request.isNotBlank(), onClick={ ask() }) { Text("Consultar") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color=MaterialTheme.colorScheme.error)
                    if (answer.isNotBlank()) Text(answer, style=MaterialTheme.typography.bodyLarge)
                    if (questions.isNotEmpty()) {
                        questions.forEachIndexed { index, question ->
                            OutlinedTextField(answers[index].orEmpty(), { answers=answers+(index to it.take(600)) },
                                Modifier.fillMaxWidth(), label={ Text(question) }, enabled=!busy)
                        }
                        OutlinedButton(enabled=!busy && request.trim()==previousRequest, onClick={ ask(true) }) { Text("Continuar") }
                    } else if (products.isNotEmpty()) {
                        Text("Productos sugeridos", style=MaterialTheme.typography.titleMedium)
                        products.forEachIndexed { index, suggestion ->
                            val product = suggestion.product
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(index in chosen, { selected -> chosen=if(selected)chosen+index else chosen-index }, enabled=!busy)
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, style=MaterialTheme.typography.titleSmall)
                                    if (suggestion.reason.isNotBlank()) Text(suggestion.reason, style=MaterialTheme.typography.bodySmall)
                                    if (index !in chosen && product.quantitySpecified)
                                        Text((amountText(product.quantity)+" "+product.unit).trim(), style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (index in chosen) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { Field(quantities[index].orEmpty(), { quantities=quantities+(index to it.take(16)) }, "Cantidad (opcional)") }
                                Column(Modifier.weight(1f)) { UnitSelector(units[index].orEmpty(), { units=units+(index to it) }) }
                            }
                        }
                    }
                }
                val valid = chosen.all { index ->
                    val value = quantities[index].orEmpty()
                    value.isBlank() || value.replace(',','.').toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 100000 } == true
                }
                if (chosen.isNotEmpty() && questions.isEmpty()) Button(enabled=!busy && valid, onClick={
                    add(chosen.sorted().map { index ->
                        val quantity = quantities[index].orEmpty().replace(',','.').toDoubleOrNull()
                        products[index].product.copy(quantity=quantity?:1.0, quantitySpecified=quantity!=null, unit=units[index].orEmpty())
                    })
                    close()
                }, modifier=Modifier.fillMaxWidth().padding(16.dp)) { Text("Añadir a la lista") }
            }
        }
    }
    if (connect) PersonalGeminiDialog(uid) { connect=false }
}
