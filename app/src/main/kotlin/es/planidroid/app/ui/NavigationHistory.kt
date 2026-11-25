package es.planidroid.app.ui
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler

internal fun pushDestination(history:List<String>,destination:String):List<String>{
    if(history.lastOrNull()==destination)return history
    return (history+destination).takeLast(64)
}
internal fun popDestination(history:List<String>):List<String> = if(history.size>1)history.dropLast(1)else history

/** Child routes register their own back action with the top bar, as well as the system back dispatcher. */
internal val LocalSectionBack=compositionLocalOf<((()->Unit)?)->Unit>{ {} }
@Composable internal fun SectionBack(active:Boolean,action:()->Unit){
    val register=LocalSectionBack.current
    val latest by rememberUpdatedState(action)
    DisposableEffect(active,register){if(active)register{latest()};onDispose{if(active)register(null)}}
    BackHandler(active){latest()}
}
