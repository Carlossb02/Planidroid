package es.planidroid.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import es.planidroid.app.ui.PlanidroidScreen
import es.planidroid.app.ui.PlanidroidModel

class MainActivity : ComponentActivity() {
    private val model: PlanidroidModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null)receiveCalendar(intent)
        setContent { PlanidroidScreen(model) }
    }

    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        setIntent(intent)
        receiveCalendar(intent)
    }

    @Suppress("DEPRECATION")
    private fun receiveCalendar(intent:Intent){
        val uri=when(intent.action){
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> runCatching {
                (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)
                    ?:intent.clipData?.takeIf{it.itemCount>0}?.getItemAt(0)?.uri
            }.getOrNull()
            else -> null
        }
        uri?.let(model::openCalendarFile)
    }
}
