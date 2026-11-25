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
    suspend fun sync(context: Context, uid: String) = mutex.withLock {
        check(auth(context)?.currentUser?.uid == uid) { "Sesión no disponible" }
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
        for(snap in coll.get(Source.SERVER).await().documents) {
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
        SessionReminders.refresh(context,uid)
        check(conflicts == 0) { "$conflicts conflictos: se conservan los cambios locales. Exporta una copia antes de resolverlos." }
    }
    fun schedule(context: Context, uid: String) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(workDataOf("uid" to uid)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync-$uid",ExistingPeriodicWorkPolicy.KEEP,request)
    }
}
class SyncWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val uid=inputData.getString("uid") ?: return Result.failure()
        if(Cloud.auth(applicationContext)?.currentUser?.uid != uid) return Result.success()
        return try { Cloud.sync(applicationContext,uid); Result.success() } catch(e: kotlinx.coroutines.CancellationException) { throw e } catch(e: Exception) { Result.retry() }
    }
}
