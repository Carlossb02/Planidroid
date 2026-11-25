package es.planidroid.app.ui
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.*
import org.json.JSONObject

internal fun safeShoppingReference(url:String):Boolean=runCatching{val u=java.net.URI(url);u.scheme=="https"&&u.host?.isNotBlank()==true&&u.userInfo==null&&url.length<=2048}.getOrDefault(false)
internal fun openShoppingReference(ctx:Context,url:String){if(safeShoppingReference(url))runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
@Composable internal fun ShoppingAiActions(recipes:()->Unit,assistant:()->Unit,enabled:Boolean=true){
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        FilledTonalButton(enabled=enabled,onClick=recipes,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)){Icon(Icons.Default.Restaurant,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Recetas")}
        FilledTonalButton(enabled=enabled,onClick=assistant,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)){Icon(Icons.Default.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Gemini")}
    }
}
private data class SuggestedProduct(val product:Shopping,val reason:String)
private data class WebSource(val title:String,val url:String)
@Composable fun ShoppingAssistant(uid:String,current:List<Shopping>,listId:String,close:()->Unit,add:(List<Shopping>)->Unit,shared:Boolean=false){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var mode by remember{mutableStateOf("Fiesta")}
    var request by remember{mutableStateOf("")};var original by remember{mutableStateOf("")}
    var questions by remember{mutableStateOf(emptyList<String>())};var answers by remember{mutableStateOf(emptyMap<Int,String>())}
    var answer by remember{mutableStateOf("")};var products by remember{mutableStateOf(emptyList<SuggestedProduct>())};var chosen by remember{mutableStateOf(emptySet<Int>())};var quantities by remember{mutableStateOf(emptyMap<Int,String>())};var productUnits by remember{mutableStateOf(emptyMap<Int,String>())}
    var sources by remember{mutableStateOf(emptyList<WebSource>())};var suggestions by remember{mutableStateOf("")};var sourceIndex by remember{mutableStateOf<Int?>(null)}
    var productName by remember{mutableStateOf("")};var quantity by remember{mutableStateOf("1")};var unit by remember{mutableStateOf("ud")}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var connect by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
    fun reset(){questions=emptyList();answers=emptyMap();answer="";products=emptyList();chosen=emptySet();sources=emptyList();suggestions="";sourceIndex=null;productName="";error=""}
    fun ask(followUp:Boolean=false){
        if(busy)return
        if(!AiConfig.configured(ctx,uid)){connect=true;return}
        val query=if(followUp)original+"\n"+questions.mapIndexed{i,q->q+": "+answers[i].orEmpty()}.joinToString("\n")else request.trim()
        if(query.isBlank())return
        if(!followUp){original=query;reset()}
        busy=true;error=""
        scope.launch{try{
            val provider=AiConfig.provider(ctx,uid)
            if(mode=="Buscar producto"){
                val result=provider.supermarketSearch(query);val grounding=result.getJSONObject("grounding");val chunks=grounding.optJSONArray("groundingChunks")
                val actual=if(chunks==null)emptyList()else (0 until minOf(chunks.length(),20)).mapNotNull{i->val w=chunks.optJSONObject(i)?.optJSONObject("web")?:return@mapNotNull null;val url=w.optString("uri");if(!safeShoppingReference(url))null else WebSource(w.optString("title").take(180).ifBlank{"Fuente web"},url)}.distinctBy{it.url}
                check(actual.isNotEmpty()){"No se obtuvieron referencias web verificables. No se guardará ningún enlace."}
                sources=actual;answer=result.optString("answer").take(5000);suggestions=grounding.optJSONObject("searchEntryPoint")?.optString("renderedContent").orEmpty().take(100000)
            }else{
                val context=current.take(40).joinToString("; "){it.name+if(it.quantitySpecified)" "+amountText(it.quantity)+" "+it.unit else ""}
                val result=provider.shoppingAdvice(mode,query,context,followUp)
                val q=result.optJSONArray("questions");questions=if(followUp||q==null)emptyList()else (0 until minOf(q.length(),2)).map{q.optString(it).take(240)}.filter{it.isNotBlank()}
                answer=result.optString("answer").take(3000)
                val a=result.optJSONArray("products");products=if(a==null)emptyList()else (0 until minOf(a.length(),40)).mapNotNull{i->val p=a.optJSONObject(i)?:return@mapNotNull null;val name=p.optString("name").trim().take(180);val n=p.optDouble("quantity");val u=SpanishUnits.canonical(p.optString("unit")).orEmpty();if(name.isBlank()||!n.isFinite()||n<=0||n>100000||u.isBlank())null else SuggestedProduct(Shopping(name=name,quantity=n,unit=u,listId=listId,icon=guessIcon(name)),p.optString("reason").take(180))}
                chosen=if(mode=="Comparar")emptySet()else products.indices.toSet();quantities=products.mapIndexed{i,p->i to amountText(p.product.quantity)}.toMap();productUnits=products.mapIndexed{i,p->i to p.product.unit}.toMap()
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.localizedMessage?:"No se pudo consultar Gemini"}finally{busy=false}}
    }
    Dialog(onDismissRequest={if(!busy)close()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()){
                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){IconButton(enabled=!busy,onClick=close){Icon(Icons.Default.Close,"Cerrar")};Icon(Icons.Default.AutoAwesome,null,tint=MaterialTheme.colorScheme.tertiary);Spacer(Modifier.width(8.dp));Text("Gemini · Compra",style=MaterialTheme.typography.titleLarge)}
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Fiesta","Comparar","Buscar producto").forEach{v->FilterChip(mode==v,{if(!busy){mode=v;request="";reset()}},label={Text(v)},enabled=!busy)}}
                    OutlinedTextField(request,{request=it.take(2000)},Modifier.fillMaxWidth(),label={Text(when(mode){"Fiesta"->"¿Qué vas a celebrar?";"Comparar"->"¿Qué productos quieres comparar?";else->"Producto y supermercado"})},minLines=3,enabled=!busy,shape=RoundedCornerShape(16.dp))
                    if(mode=="Buscar producto")Text("Búsqueda web opcional. Puede consumir cuota adicional.",style=MaterialTheme.typography.bodySmall)
                    Button(enabled=!busy&&request.isNotBlank(),onClick={if(mode=="Buscar producto")confirm=true else ask()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(6.dp));Text(if(mode=="Buscar producto")"Buscar en Internet"else "Consultar Gemini")}
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
                    if(questions.isNotEmpty())GlassCard(tint=Lilac){Text("Un par de detalles",style=MaterialTheme.typography.titleMedium);questions.forEachIndexed{i,q->Text(q);OutlinedTextField(answers[i].orEmpty(),{answers=answers+(i to it.take(1200))},Modifier.fillMaxWidth(),placeholder={Text("Responde con tus palabras")},minLines=2,enabled=!busy)};Button(enabled=!busy&&answers.values.any{it.isNotBlank()},onClick={ask(true)}){Text("Continuar")};TextButton(enabled=!busy,onClick={ask(true)}){Text("Usar una estimación")}}
                    if(answer.isNotBlank())GlassCard(tint=Lilac){Text(answer)}
                    if(products.isNotEmpty()&&questions.isEmpty()){
                        Row(verticalAlignment=Alignment.CenterVertically){Text("Productos",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={chosen=if(chosen.size==products.size)emptySet()else products.indices.toSet()}){Text(if(chosen.size==products.size)"Ninguno"else "Todos")}}
                        products.forEachIndexed{i,p->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(i in chosen,{v->chosen=if(v)chosen+i else chosen-i});ProductGlyph(p.product.name,Modifier.size(26.dp));Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(p.product.name);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Column(Modifier.weight(1f)){Field(quantities[i].orEmpty(),{v->quantities=quantities+(i to v.take(12))},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(productUnits[i]?:p.product.unit,{v->productUnits=productUnits+(i to v)},allowEmpty=false)}};if(p.reason.isNotBlank())Text(p.reason,style=MaterialTheme.typography.bodySmall)}}}
                    }
                    if(sources.isNotEmpty()){
                        Text("Fuentes de Google",style=MaterialTheme.typography.titleMedium)
                        sources.forEachIndexed{i,s->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=sourceIndex==i,onClick={sourceIndex=i});Text(s.title,Modifier.weight(1f),maxLines=2);IconButton(onClick={openShoppingReference(ctx,s.url)}){Icon(Icons.Default.OpenInNew,"Abrir fuente")}}}
                        if(suggestions.isNotBlank())SearchSuggestions(suggestions)
                        if(sourceIndex!=null){Field(productName,{productName=it.take(180)},"Nombre exacto del producto");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Field(quantity,{quantity=it.take(12)},"Cantidad")};Column(Modifier.weight(1f)){UnitSelector(unit,{unit=it},allowEmpty=false)}};Text(if(shared)"El nombre y el enlace se compartirán con esta lista."else "Comprueba la ficha antes de añadir.",style=MaterialTheme.typography.bodySmall)}
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val n=quantity.replace(',','.').toDoubleOrNull()
                val validProducts=chosen.isNotEmpty()&&chosen.all{i->quantities[i]?.replace(',','.')?.toDoubleOrNull()?.let{it.isFinite()&&it>0&&it<=100000}==true}
                val validRef=sourceIndex!=null&&productName.isNotBlank()&&n!=null&&n.isFinite()&&n>0&&n<=100000&&unit.isNotBlank()
                Surface(tonalElevation=2.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){
                    Button(enabled=!busy&&questions.isEmpty()&&(validProducts||validRef),onClick={val source=sourceIndex?.let{sources.getOrNull(it)};val selected=if(validRef&&source!=null)listOf(Shopping(name=productName.trim(),quantity=n!!,unit=unit,listId=listId,icon=guessIcon(productName),referenceUrl=source.url,referenceTitle=source.title))else products.mapIndexedNotNull{i,p->if(i in chosen)p.product.copy(quantity=quantities.getValue(i).replace(',','.').toDouble(),unit=productUnits[i]?:p.product.unit)else null};add(selected);close()},modifier=Modifier.fillMaxWidth()){Text(if(validRef)"Añadir producto con referencia"else "Añadir "+chosen.size+" productos")}
                }}
            }
        }
    }
    if(confirm)Confirm("Buscar en Google","Puede consumir cuota de búsqueda o generar cargos en tu proyecto personal. ¿Continuar?",{confirm=false},{ask()})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}
@Composable private fun SearchSuggestions(html:String){
    val ctx=LocalContext.current
    AndroidView(factory={context->WebView(context).apply{
        settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.blockNetworkLoads=true;settings.allowFileAccessFromFileURLs=false;settings.allowUniversalAccessFromFileURLs=false
        webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{request?.url?.toString()?.let{openShoppingReference(ctx,it)};return true}}
    }},onRelease={it.stopLoading();it.destroy()},update={it.loadDataWithBaseURL(null,html,"text/html","UTF-8",null)},modifier=Modifier.fillMaxWidth().height(104.dp))
}
