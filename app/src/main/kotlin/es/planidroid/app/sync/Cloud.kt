package es.planidroid.app.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.work.*
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import es.planidroid.app.data.*
import es.planidroid.app.domain.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

object Cloud {
    private val mutex = Mutex()
    fun configured(context: Context) = FirebaseApp.getApps(context).isNotEmpty() || runCatching { FirebaseApp.initializeApp(context) != null }.getOrDefault(false)
    fun auth(context: Context): FirebaseAuth? = if(configured(context)) FirebaseAuth.getInstance() else null
    suspend fun login(context: Context) {
        check(configured(context)) { "Falta app/google-services.json" }
        val res = context.resources.getIdentifier("default_web_client_id","string",context.packageName)
        check(res != 0) { "Falta el cliente OAuth web; habilita Google en Firebase y descarga de nuevo google-services.json" }
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(context.getString(res)).setAutoSelectEnabled(false).build()
        val result = CredentialManager.create(context).getCredential(context,GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
        check(result is CustomCredential && result.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Credencial no compatible" }
        val token = GoogleIdTokenCredential.createFrom(result.data).idToken
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(token,null)).await()
    }
    class ApprovalRequired : IllegalStateException("Tu cuenta está pendiente de autorización. Solicita al administrador que habilite tu UID.")
    private fun prefs(context:Context,uid:String)=context.getSharedPreferences("cloud-sync-$uid",Context.MODE_PRIVATE)
    fun automatic(context:Context,uid:String)=prefs(context,uid).getBoolean("automatic",false)
    suspend fun requireApproval(context:Context,uid:String) {
        check(auth(context)?.currentUser?.uid==uid){"Sesión no disponible"}
        val snapshot=FirebaseFirestore.getInstance().collection("approvedUsers").document(uid).get(Source.SERVER).await()
        if(snapshot.getBoolean("enabled")!=true)throw ApprovalRequired()
    }
    fun stop(context:Context,uid:String) {
        if(uid!="local")WorkManager.getInstance(context).cancelUniqueWork("sync-$uid")
    }
    fun setAutomatic(context:Context,uid:String,enabled:Boolean){
        prefs(context,uid).edit().putBoolean("automatic",enabled).apply()
        if(enabled)schedule(context,uid)else stop(context,uid)
    }
    suspend fun sync(context: Context, uid: String) = mutex.withLock {
        check(auth(context)?.currentUser?.uid == uid) { "Sesión no disponible" }
        requireApproval(context,uid)
        val dao = PlanidroidDb.get(context).records()
        val db = FirebaseFirestore.getInstance()
        val coll = db.collection("users").document(uid).collection("records")
        var conflicts = 0
        for (row in dao.all(uid).filter { it.dirty }) {
            check(auth(context)?.currentUser?.uid == uid)
            val payload = when(row.kind) { Kind.TASK.name -> encode(decode<Task>(row.payload).copy(attachments=emptyList(),contact="",phone="")); Kind.FURNITURE.name -> encode(decode<Furniture>(row.payload).copy(photo="")); Kind.ITEM.name -> encode(decode<Item>(row.payload).copy(photo="")); else -> row.payload }
            val version = db.runTransaction { tx ->
                val ref = coll.document(row.id); val snap = tx.get(ref)
                val current = snap.getLong("version") ?: 0L
                if (current != row.baseVersion) -1L else {
                    tx.set(ref, mapOf("kind" to row.kind,"payload" to payload,"deleted" to row.deleted,"version" to current+1,"updatedAt" to FieldValue.serverTimestamp()))
                    current+1
                }
            }.await()
            if(version < 0) conflicts++ else dao.ack(uid,row.id,row.revision,version)
        }
        // Pull only changes since the last fully processed server timestamp.
        // An inclusive lower bound avoids losing records sharing a timestamp.
        val p=prefs(context,uid)
        val seconds=p.getLong("cursorSeconds",-1L)
        val cursor=if(seconds>=0)Timestamp(seconds,p.getInt("cursorNanos",0))else null
        var newest=cursor
        val query=if(cursor==null)coll else coll.whereGreaterThanOrEqualTo("updatedAt",cursor)
        for(snap in query.get(Source.SERVER).await().documents) {
            val timestamp=snap.getTimestamp("updatedAt")
            if(timestamp!=null&&(newest==null||timestamp>newest!!))newest=timestamp
            check(auth(context)?.currentUser?.uid == uid)
            val old = dao.get(uid,snap.id)
            val version = snap.getLong("version") ?: continue
            if(old?.dirty == true || (old != null && old.baseVersion >= version)) continue
            val kind = snap.getString("kind") ?: continue
            if(Kind.entries.none { it.name==kind }) continue
            var payload = snap.getString("payload") ?: continue
            if(kind==Kind.TASK.name && old != null) {
                val previous=decode<Task>(old.payload)
                payload=encode(decode<Task>(payload).copy(attachments=previous.attachments,contact=previous.contact,phone=previous.phone))
            }
            if(kind==Kind.FURNITURE.name && old!=null) payload=encode(decode<Furniture>(payload).copy(photo=decode<Furniture>(old.payload).photo))
            if(kind==Kind.ITEM.name && old!=null) payload=encode(decode<Item>(payload).copy(photo=decode<Item>(old.payload).photo))
            dao.acceptRemote(Record(uid,snap.id,kind,payload,snap.getBoolean("deleted") ?: false,false,version,(old?.revision ?: 0)+1));if(kind==Kind.TASK.name&&snap.getBoolean("deleted")!=true)runCatching{Reminders.update(context,uid,decode<Task>(payload))}
        }
        newest?.let{p.edit().putLong("cursorSeconds",it.seconds).putInt("cursorNanos",it.nanoseconds).commit()}
        SessionReminders.refresh(context,uid)
        check(conflicts == 0) { "$conflicts conflictos: se conservan los cambios locales. Exporta una copia antes de resolverlos." }
    }
    fun schedule(context: Context, uid: String) {
        if(!automatic(context,uid)){stop(context,uid);return}
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("uid" to uid)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync-$uid",ExistingPeriodicWorkPolicy.UPDATE,request)
    }
}
class SyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val uid=inputData.getString("uid") ?: return Result.failure()
        if(Cloud.auth(applicationContext)?.currentUser?.uid != uid || !Cloud.automatic(applicationContext,uid)) return Result.success()
        return try { Cloud.sync(applicationContext,uid); Result.success() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Cloud.ApprovalRequired) { Cloud.setAutomatic(applicationContext,uid,false); Result.success() } catch(e: FirebaseFirestoreException) { if(e.code==FirebaseFirestoreException.Code.PERMISSION_DENIED){Cloud.setAutomatic(applicationContext,uid,false);Result.success()}else Result.success() } catch(e: Exception) { Result.success() }
    }
}
