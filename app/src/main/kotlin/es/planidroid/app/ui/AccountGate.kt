package es.planidroid.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun AccountGate(model:PlanidroidModel,uid:String,verified:Boolean?){
    val ctx=LocalContext.current;val busy by model.busy.collectAsStateWithLifecycle();val message by model.message.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        Surface(shape=RoundedCornerShape(28.dp),color=soft(Blue,.18f)){Icon(Icons.Default.Checklist,null,Modifier.padding(20.dp).size(48.dp),tint=MaterialTheme.colorScheme.primary)}
        Spacer(Modifier.height(14.dp))
        Text("Planidroid",style=MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(18.dp))
        if(uid=="local"){Text("Organiza tus días, a tu ritmo.",style=MaterialTheme.typography.titleMedium);Text("Inicia sesión con Google para solicitar acceso a Planidroid.");Button(enabled=!busy&&model.configured(),onClick={model.login(ctx)},modifier=Modifier.fillMaxWidth()){Text("Continuar con Google")};if(!model.configured())Text("Firebase no está configurado en esta instalación.")}
        else{ProfileAvatar(uid,onClick={});Text(model.email());Text(if(verified==false)"Pendiente de autorización" else "Comprueba tu autorización",style=MaterialTheme.typography.titleLarge)
            Text("El administrador debe autorizar tu cuenta una vez con conexión. Después podrás utilizar tus datos sin Internet.")
            OutlinedButton(enabled=!busy,onClick={model.retryApproval()}){Text("Comprobar acceso")}
            TextButton(onClick={val clipboard=ctx.getSystemService(android.content.ClipboardManager::class.java);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UID",uid))}){Text("Copiar UID")}
            TextButton(enabled=!busy,onClick={model.logout()}){Text("Cerrar sesión")}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }}
}
