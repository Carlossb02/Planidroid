package es.planidroid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A single sign-in prompt for optional online features; no data is shared by signing in. */
val LocalOnlineAccess = compositionLocalOf<(String)->Unit> { {} }

@Composable fun OnlineSignInDialog(feature:String,model:PlanidroidModel,uid:String,close:()->Unit){
    val ctx=LocalContext.current
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val configured=model.configured()
    var attempted by remember(feature){mutableStateOf(false)}
    LaunchedEffect(uid){if(uid!="local")close()}
    AlertDialog(
        onDismissRequest={if(!busy)close()},
        shape=RoundedCornerShape(28.dp),
        icon={Surface(shape=RoundedCornerShape(22.dp),color=soft(Blue,.16f)){Icon(Icons.Outlined.Cloud,null,Modifier.padding(17.dp).size(30.dp),tint=MaterialTheme.colorScheme.primary)}},
        title={Text("Conecta tu cuenta",style=MaterialTheme.typography.headlineSmall)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text("Para "+feature+" necesitas iniciar sesión con Google y tener la cuenta autorizada por el administrador.")
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Icon(Icons.Outlined.Lock,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("Tus datos locales siguen separados de tu cuenta. Nada se comparte sin tu permiso.",style=MaterialTheme.typography.bodySmall)
                }
                if(!configured)Text("Google todavía no está configurado en esta instalación. Puedes seguir usando Planidroid sin conexión.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                if(attempted&&message.isNotBlank()&&!busy)Text(message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton={if(configured)Button(onClick={attempted=true;model.login(ctx)},enabled=!busy,shape=RoundedCornerShape(16.dp)){Text(if(busy)"Conectando…" else "Continuar con Google")}else TextButton(onClick=close){Text("Entendido")}},
        dismissButton={if(configured)TextButton(onClick=close,enabled=!busy){Text("Ahora no")}}
    )
}
