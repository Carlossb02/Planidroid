package es.planidroid.app.ui
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.planidroid.app.domain.*
import es.planidroid.app.sync.AiConfig
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

internal suspend fun shoppingPhotoBytes(ctx:Context,uri:Uri):ByteArray=withContext(Dispatchers.IO){
    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
    ctx.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
    require(options.outWidth>0&&options.outHeight>0)
    var sample=1;while(maxOf(options.outWidth,options.outHeight)/sample>1600)sample*=2
    val bitmap=ctx.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}?:error("Imagen inválida")
    try{ByteArrayOutputStream().use{out->bitmap.compress(Bitmap.CompressFormat.JPEG,75,out);out.toByteArray().also{require(it.size<=2_000_000)}}}finally{bitmap.recycle()}
}

@Composable fun RecipeSuggestions(uid:String,ingredients:List<String>,listId:String,close:()->Unit,add:(List<Shopping>)->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var request by remember{mutableStateOf("Sugiere 2 recetas sencillas para esta lista")}
    var answer by remember{mutableStateOf("")};var products by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var connect by remember{mutableStateOf(false)}
    val suggested=remember(products){ShoppingText.parse(products,listId).filter{p->ingredients.none{it.equals(p.name,true)}}.distinctBy{it.name.lowercase()}}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Recetas con Gemini")},text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Se enviarán únicamente los nombres de los productos de esta lista y tu petición a Gemini.")
        Field(request,{request=it.take(2000)},"¿Qué quieres cocinar?",false)
        Button(enabled=!busy&&request.isNotBlank(),onClick={if(!AiConfig.configured(ctx,uid))connect=true else {busy=true;error="";scope.launch{try{val response=AiConfig.provider(ctx,uid).recipe(ingredients.take(100).joinToString(", "),request);answer=response.getString("answer");val a=response.optJSONArray("products");products=if(a==null)"" else (0 until minOf(a.length(),100)).joinToString("\n"){a.getString(it).take(180)}}catch(e:CancellationException){throw e}catch(_:Exception){error="Gemini no disponible. Puedes seguir usando la lista."}finally{busy=false}}}}}){Text("Generar sugerencias")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(answer.isNotBlank()){Text(answer);Field(products,{products=it.take(16000)},"Ingredientes sugeridos (revisa antes de añadir)",false)}
    }},confirmButton={TextButton(enabled=!busy&&suggested.isNotEmpty(),onClick={add(suggested);close()}){Text("Añadir "+suggested.size+" productos")}},dismissButton={TextButton(enabled=!busy,onClick=close){Text("Cerrar")}})
    if(connect)PersonalGeminiDialog(uid){connect=false}
}
