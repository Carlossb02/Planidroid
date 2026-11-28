package es.planidroid.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.*
import kotlin.random.Random

internal enum class FocusAmbience(val label:String){
    SILENT("Silencio"), MUSIC("Música de estudio"), OCEAN("Mar"), WIND("Viento")
}

private const val SampleRate=22050
private const val FullTurn=2*PI

@Composable internal fun FocusSound(ambience:FocusAmbience,playing:Boolean,volume:Float):Boolean{
    val context=LocalContext.current
    var playback by remember{mutableStateOf<FocusPlayback?>(null)}
    var unavailable by remember{mutableStateOf(false)}
    val desiredVolume by rememberUpdatedState(volume)
    LaunchedEffect(ambience,playing,context){
        unavailable=false
        if(!playing||ambience==FocusAmbience.SILENT)return@LaunchedEffect
        val samples=withContext(Dispatchers.Default){focusSamples(ambience){ensureActive()}}
        val output=runCatching{FocusPlayback(context,samples)}.getOrNull()
        if(output==null){unavailable=true;return@LaunchedEffect}
        playback=output
        try{
            output.volume(desiredVolume)
            if(!runCatching{output.start()}.getOrDefault(false)){unavailable=true;return@LaunchedEffect}
            awaitCancellation()
        }finally{
            playback=null
            output.close()
        }
    }
    SideEffect{playback?.volume(volume)}
    return unavailable
}

private class FocusPlayback(context:Context,samples:ShortArray){
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var focused=false
    private var closed=false
    private val track=AudioTrack.Builder().setAudioAttributes(attributes)
        .setAudioFormat(AudioFormat.Builder().setSampleRate(SampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(samples.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
    private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener{change->
            if(!closed&&focused){
                if(change==AudioManager.AUDIOFOCUS_GAIN)track.play()
                else{
                    track.pause()
                    if(change==AudioManager.AUDIOFOCUS_LOSS)focused=false
                }
            }
        }.build()

    init{
        try{
            check(track.write(samples,0,samples.size)==samples.size)
            check(track.setLoopPoints(0,samples.size,-1)==AudioTrack.SUCCESS)
        }catch(failure:Exception){
            track.release()
            throw failure
        }
    }

    fun start():Boolean {
        focused=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if(focused)track.play()
        return focused
    }

    fun volume(value:Float){if(!closed)track.setVolume(value)}

    fun close(){
        closed=true
        manager.abandonAudioFocusRequest(request)
        track.release()
    }
}

internal fun focusSamples(ambience:FocusAmbience,checkActive:()->Unit={}):ShortArray{
    if(ambience==FocusAmbience.SILENT)return ShortArray(0)
    val music=ambience==FocusAmbience.MUSIC
    val length=if(music)32 else 20
    val samples=ShortArray(length*SampleRate)
    val random=Random(37)
    var low=0.0
    var middle=0.0
    var drift=0.0
    val notes=listOf(
        listOf(48,55,59,64),listOf(45,52,55,60),listOf(41,48,57,64),listOf(43,50,57,62)
    ).map{chord->chord.map{note->440*2.0.pow((note-69)/12.0)}}
    for(index in samples.indices){
        if(index%4096==0)checkActive()
        val t=index.toDouble()/SampleRate
        val value=if(music){
            val beat=(t/8).toInt()
            val local=t%8
            val chord=notes[beat]
            val padEnvelope=minOf(local/.85,(8-local)/1.25,1.0)
            var mix=0.0
            for(note in chord.indices){
                val frequency=chord[note]
                val angle=FullTurn*frequency*local
                mix+=(sin(angle)+.22*sin(angle*2))*.025*padEnvelope
                val offset=local-.45-note*1.55
                if(offset in 0.0..3.1){
                    val pluck=FullTurn*frequency*2*offset
                    val envelope=minOf(offset/.12,(3.1-offset)/.3,1.0)*exp(-1.7*offset)
                    mix+=(sin(pluck)+.34*sin(pluck*2))*.065*envelope
                }
            }
            mix
        }else{
            val noise=random.nextDouble(-1.0,1.0)
            low+=(noise-low)*.017
            middle+=(noise-middle)*.10
            drift+=(low-drift)*.005
            val tide=.48+.33*sin(FullTurn*t/7.3-.9)+.15*sin(FullTurn*t/3.5)
            if(ambience==FocusAmbience.OCEAN)(low*.52+(middle-low)*.32)*tide + drift*.18
            else (drift*3.9+low*.37+(middle-low)*.13)*(.56+.34*sin(FullTurn*t/9.2))
        }
        val fade=minOf(t/.35,(samples.lastIndex-index).toDouble()/SampleRate/.35,1.0)
        samples[index]=(value*fade*Short.MAX_VALUE).toInt().coerceIn(-32767,32767).toShort()
    }
    return samples
}
