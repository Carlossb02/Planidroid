package es.planidroid.app.ui
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.planidroid.app.sync.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable fun PersonalGeminiDialog(uid:String,close:()->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var configured by remember(uid){mutableStateOf(AiConfig.configured(ctx,uid))}
    var secret by remember(uid){mutableStateOf("")};var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest={if(!busy)close()},title={Text("Tu Gemini")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(if(configured)"Clave personal guardada en este dispositivo." else "Gemini es opcional. Utiliza una clave de tu propio proyecto de Google AI Studio.")
        Text("La clave se cifra en este teléfono. No se sincroniza ni se exporta. Las peticiones autorizadas van directamente a Google y consumen tu cuota personal.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://aistudio.google.com/api-keys")))}){Text("Obtener clave personal")}
        OutlinedTextField(secret,{secret=it.take(512)},label={Text(if(configured)"Reemplazar clave" else "Clave API")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),enabled=!busy)
        Button(enabled=!busy&&secret.isNotBlank(),onClick={try{PersonalGeminiKey.save(ctx,uid,secret.trim());secret="";configured=true;status="Guardada. Pulsa comprobar si quieres probarla."}catch(_:Exception){status="No se pudo guardar la clave"}}){Text("Guardar clave")}
        if(configured){OutlinedButton(enabled=!busy,onClick={busy=true;scope.launch{try{AiConfig.provider(ctx,uid).answerWithUserContext("Responde OK.",emptyList());status="Gemini funciona"}catch(e:CancellationException){throw e}catch(_:Exception){status="No se pudo comprobar Gemini. Revisa tu clave y conexión."}finally{busy=false}}}){Text("Comprobar Gemini")};TextButton(enabled=!busy,onClick={runCatching{PersonalGeminiKey.remove(ctx,uid)}.onSuccess{configured=false;secret="";status="Clave eliminada"}.onFailure{status="No se pudo eliminar la clave"}}){Text("Eliminar clave")}}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(status.isNotBlank())Text(status)
    }},confirmButton={TextButton(enabled=!busy,onClick=close){Text("Cerrar")}})
}
