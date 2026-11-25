package es.planidroid.app.sync
import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import es.planidroid.app.data.*
import es.planidroid.app.domain.*
import kotlinx.coroutines.tasks.await

data class CloudConflict(val local:Record,val remote:Record)
object ConflictResolution {
    private fun collection(ctx:Context,uid:String):com.google.firebase.firestore.CollectionReference {check(Cloud.auth(ctx)?.currentUser?.uid==uid&&uid!="local");return FirebaseFirestore.getInstance().collection("users").document(uid).collection("records")}
    suspend fun list(ctx:Context,uid:String):List<CloudConflict>{val coll=collection(ctx,uid);val rows=PlanidroidDb.get(ctx).records().all(uid).filter{it.dirty};Cloud.requireApproval(ctx,uid);return rows.mapNotNull{r->val d=coll.document(r.id).get(Source.SERVER).await();if(!d.exists())return@mapNotNull null;val version=d.getLong("version")?:return@mapNotNull null;if(version==r.baseVersion)return@mapNotNull null;val payload=d.getString("payload")?:return@mapNotNull null;CloudConflict(r,Record(uid,r.id,d.getString("kind")?:return@mapNotNull null,payload,d.getBoolean("deleted")?:false,false,version,r.revision+1))}}
    suspend fun resolve(ctx:Context,uid:String,c:CloudConflict,keepLocal:Boolean){val d=collection(ctx,uid).document(c.local.id).get(Source.SERVER).await();check(d.getLong("version")==c.remote.baseVersion){"La versión remota ha cambiado. Actualiza la lista."};val db=PlanidroidDb.get(ctx);val dao=db.records();db.withTransaction{val current=dao.get(uid,c.local.id)?:error("Registro no disponible");check(current.revision==c.local.revision&&current.baseVersion==c.local.baseVersion&&current.dirty){"El registro local ha cambiado. Actualiza la lista."};if(keepLocal){dao.put(current.copy(baseVersion=c.remote.baseVersion,dirty=true,revision=current.revision+1))}else{val backup=BackupIO.create(dao.all(uid),"Sistema",false);val folder=java.io.File(ctx.filesDir,"recovery/$uid").apply{mkdirs()};kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){java.io.File(folder,"conflict-${System.currentTimeMillis()}.json").writeText(encode(backup))};val remote=c.remote;val payload=when(remote.kind){Kind.TASK.name->encode(decode<Task>(remote.payload).copy(attachments=decode<Task>(current.payload).attachments,contact=decode<Task>(current.payload).contact,phone=decode<Task>(current.payload).phone));Kind.FURNITURE.name->encode(decode<Furniture>(remote.payload).copy(photo=decode<Furniture>(current.payload).photo));Kind.ITEM.name->encode(decode<Item>(remote.payload).copy(photo=decode<Item>(current.payload).photo));else->remote.payload};dao.put(remote.copy(payload=payload,revision=current.revision+1))}};SessionReminders.refresh(ctx,uid)}
}
