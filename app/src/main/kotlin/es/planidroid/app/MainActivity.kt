package es.planidroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import es.planidroid.app.ui.PlanidroidScreen
import es.planidroid.app.ui.PlanidroidModel

class MainActivity : ComponentActivity() {
    private val model: PlanidroidModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { PlanidroidScreen(model) } }
}
