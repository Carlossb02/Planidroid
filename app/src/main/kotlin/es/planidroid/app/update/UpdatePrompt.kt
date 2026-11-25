package es.planidroid.app.update

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable fun UpdatePrompt() {
    val context = LocalContext.current
    val updater = remember(context) { GithubUpdater(context) }
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(updater) {
        try { release = updater.check() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Offline startup is normal. */ }
    }
    release?.let { candidate ->
        AlertDialog(onDismissRequest = { if (!busy) { file?.delete(); release = null } },
            title = { Text("Planidroid ${candidate.tag} disponible") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nueva versión estable. Tus datos se conservan al instalar una actualización firmada con la misma clave.")
                if (candidate.notes.isNotBlank()) Text(candidate.notes, Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Descargando y verificando…") }
                if (file != null) Text("APK verificado. Si Android solicita permiso para instalar aplicaciones, actívalo y vuelve a pulsar Instalar.")
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val ready = file
                if (ready != null) { try { updater.install(ready) } catch (e: Exception) { error = e.message ?: "No se pudo abrir el instalador" } }
                else scope.launch {
                    busy = true; error = ""
                    try { file = updater.download(candidate) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Error de descarga" } finally { busy = false }
                }
            }) { Text(if (file == null) "Descargar" else "Instalar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { file?.delete(); release = null }) { Text("Más tarde") } })
    }
}
