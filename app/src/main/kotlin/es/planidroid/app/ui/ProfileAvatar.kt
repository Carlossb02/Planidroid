package es.planidroid.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.planidroid.app.sync.Cloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@Composable fun ProfileAvatar(uid:String,onClick:()->Unit){
    val context=LocalContext.current
    val user=remember(uid){Cloud.auth(context)?.currentUser?.takeIf{it.uid==uid}}
    val name=user?.displayName.orEmpty().ifBlank{user?.email.orEmpty().substringBefore('@')}
    val initials=name.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()}
    val photo=user?.photoUrl?.toString().orEmpty()
    val bitmap by produceState<Bitmap?>(null,uid,photo){
        value=null
        if(photo.isNotBlank())value=withContext(Dispatchers.IO){
            try{
                val url=URL(photo)
                if(url.protocol!="https")null else {
                    val connection=url.openConnection() as HttpsURLConnection
                    try{
                        connection.connectTimeout=5000;connection.readTimeout=5000;connection.instanceFollowRedirects=false
                        if(connection.responseCode!=200)null else connection.inputStream.use{stream->
                            val bytes=stream.readNBytesCompat(1024*1024+1)
                            if(bytes.size>1024*1024)null else {
                                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                                if(bounds.outWidth<=0||bounds.outHeight<=0)null else {
                                    var sample=1
                                    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
                                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})
                                }
                            }
                        }
                    }finally{connection.disconnect()}
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){null}
        }
    }
    Surface(onClick=onClick,shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(44.dp).semantics{contentDescription="Perfil y ajustes"}){
        Box(contentAlignment=Alignment.Center){
            val image=bitmap
            if(image!=null) Image(image.asImageBitmap(),null,Modifier.fillMaxSize().clip(CircleShape),contentScale=ContentScale.Crop)
            else if(initials.isNotBlank())Text(initials,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            else Icon(Icons.Outlined.Person,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
private fun java.io.InputStream.readNBytesCompat(limit:Int):ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(out.size()<limit){val n=read(buffer,0,minOf(buffer.size,limit-out.size()));if(n<=0)break;out.write(buffer,0,n)}
    return out.toByteArray()
}
