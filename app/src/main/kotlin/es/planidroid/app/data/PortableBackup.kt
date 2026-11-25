package es.planidroid.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import es.planidroid.app.domain.*
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** JSON is authoritative; a ZIP optionally carries private, explicitly selected binary data. */
object PortableBackup {
    private const val PREFIX="planidroid-bundle:"
    private const val MAX_FILE=20L*1024*1024
    private const val MAX_TOTAL=150L*1024*1024
    private const val MAX_JSON=8L*1024*1024
    private val validName=Regex("files/[a-f0-9-]{36}\\.[a-z0-9]{1,8}")
    private fun transform(b:Backup, mapper:(String)->String):Backup=b.copy(entries=b.entries.map{e->val payload=when(e.kind){Kind.TASK->encode(decode<Task>(e.payload).let{t->t.copy(attachments=t.attachments.map{it.copy(uri=mapper(it.uri))})});Kind.FURNITURE->encode(decode<Furniture>(e.payload).let{it.copy(photo=mapper(it.photo))});Kind.ITEM->encode(decode<Item>(e.payload).let{it.copy(photo=mapper(it.photo))});else->e.payload};e.copy(payload=payload)})
    fun stripReferences(b:Backup):Backup=transform(b){""}
    data class Written(val included:Int,val missing:Int)
    suspend fun write(ctx:Context,b:Backup,out:Uri):Written=withContext(Dispatchers.IO){
        var total=0L;var included=0;var missing=0;val done=mutableMapOf<String,String>()
        val stream=ctx.contentResolver.openOutputStream(out)?:error("No se pudo abrir el destino")
        ZipOutputStream(stream).use{zip->
            val portable=transform(b){original->if(original.isBlank())"" else done.getOrPut(original){
                val uri=Uri.parse(original);val input=if(uri.scheme=="content")runCatching{ctx.contentResolver.openInputStream(uri)}.getOrNull() else null
                if(input==null){missing++;""}else{
                    require(included<500){"Demasiados adjuntos para una sola copia"};val ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(ctx.contentResolver.getType(uri)).orEmpty().lowercase().takeIf{it.matches(Regex("[a-z0-9]{1,8}"))}?:"bin"
                    val name="files/${newId()}.$ext";zip.putNextEntry(ZipEntry(name));input.use{source->val count=copyBounded(source,MAX_FILE){buf,n->total+=n;require(total<=MAX_TOTAL){"Copia de archivos superior a 150 MB"};zip.write(buf,0,n)};require(count>=0)};zip.closeEntry();included++;PREFIX+name
                }
            }}
            val bytes=encode(portable).toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_JSON);zip.putNextEntry(ZipEntry("backup.json"));zip.write(bytes);zip.closeEntry()
        };Written(included,missing)
    }
    data class Imported(val backup:Backup,val directory:File,val count:Int)
    suspend fun read(ctx:Context,uri:Uri):Imported=withContext(Dispatchers.IO){
        val folder=File(ctx.filesDir,"imports/${newId()}").apply{check(mkdirs())};var json:String?=null;val found=mutableMapOf<String,File>();var total=0L
        try{val source=ctx.contentResolver.openInputStream(uri)?:error("No se pudo abrir el ZIP");ZipInputStream(source).use{zip->while(true){val e=zip.nextEntry?:break;require(!e.isDirectory){"Entrada no admitida"};when{e.name=="backup.json"->{require(json==null);val buf=java.io.ByteArrayOutputStream();copyBounded(zip,MAX_JSON){b,n->buf.write(b,0,n)};json=buf.toString("UTF-8")};validName.matches(e.name)->{require(found.size<500&&!found.containsKey(e.name));val file=File(folder,e.name.substringAfter('/'));file.outputStream().use{out->copyBounded(zip,MAX_FILE){b,n->total+=n;require(total<=MAX_TOTAL);out.write(b,0,n)}};found[e.name]=file};else->error("Ruta inesperada en copia ZIP")};zip.closeEntry()}}
            val b=BackupIO.validate(json?:error("Falta backup.json"));val imported=transform(b){ref->if(ref.isBlank())"" else {require(ref.startsWith(PREFIX)){"Referencia de archivo no válida"};val f=found[ref.removePrefix(PREFIX)]?:error("Falta un archivo de la copia");FileProvider.getUriForFile(ctx,"${ctx.packageName}.files",f).toString()}};Imported(imported,folder,found.size)
        }catch(e:Exception){folder.deleteRecursively();throw e}
    }
    private inline fun copyBounded(input:InputStream,limit:Long,write:(ByteArray,Int)->Unit):Long{val buffer=ByteArray(8192);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=limit){"Archivo demasiado grande"};write(buffer,n)};return total}
}
