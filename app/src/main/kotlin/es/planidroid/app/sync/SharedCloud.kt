package es.planidroid.app.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class SharedSpace(val id:String,val owner:String,val kind:String,val title:String,val body:String,val version:Long,val role:String)
data class Membership(val id:String,val space:String,val email:String,val role:String,val status:String,val acceptedUid:String="",val expiresAt:Long=0)
data class SharedComment(val id:String,val author:String,val body:String)
class SharedCloud(context:Context){
    private val auth=Cloud.auth(context) ?: error("Configura Firebase primero")
    private val user=auth.currentUser ?: error("Inicia sesión")
    private val uid=user.uid
    private val email=user.email ?: error("La cuenta no tiene correo")
    private val db=FirebaseFirestore.getInstance()
    private val spaces=db.collection("sharedSpaces");private val members=db.collection("memberships")
    private fun checkUser(){check(auth.currentUser?.uid==uid){"La cuenta ha cambiado"}}
    private fun memberId(space:String,email:String)="$space~$email"
    private fun membership(d:com.google.firebase.firestore.DocumentSnapshot)=Membership(d.id,d.getString("spaceId").orEmpty(),d.getString("email").orEmpty(),d.getString("role").orEmpty(),d.getString("status").orEmpty(),d.getString("acceptedUid").orEmpty(),d.getTimestamp("expiresAt")?.toDate()?.time?:0)
    private fun space(d:com.google.firebase.firestore.DocumentSnapshot,role:String)=SharedSpace(d.id,d.getString("ownerUid").orEmpty(),d.getString("kind").orEmpty(),d.getString("title").orEmpty(),d.getString("body").orEmpty(),d.getLong("version")?:0,role)
    suspend fun list():Pair<List<SharedSpace>,List<Membership>>{checkUser();val own=spaces.whereEqualTo("ownerUid",uid).get(Source.SERVER).await().documents.map{space(it,"owner")};val mine=members.whereEqualTo("email",email).get(Source.SERVER).await().documents.map(::membership);val accepted=mine.filter{it.status=="accepted"&&it.acceptedUid==uid}.mapNotNull{m->runCatching{spaces.document(m.space).get(Source.SERVER).await()}.getOrNull()?.takeIf{it.exists()}?.let{space(it,m.role)}};checkUser();return (own+accepted).distinctBy{it.id} to mine.filter{it.status=="pending"&&it.expiresAt>System.currentTimeMillis()}}
    suspend fun create(kind:String,title:String,body:String){checkUser();require(title.isNotBlank());spaces.document(UUID.randomUUID().toString()).set(mapOf("ownerUid" to uid,"kind" to kind,"title" to title.trim(),"body" to body,"version" to 1L,"updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun update(s:SharedSpace,title:String,body:String){checkUser();db.runTransaction{tx->val ref=spaces.document(s.id);val old=tx.get(ref);check(old.getLong("version")==s.version){"Otro participante ha editado este espacio. Actualiza antes de guardar."};tx.update(ref,mapOf("title" to title.trim(),"body" to body,"version" to s.version+1,"updatedAt" to FieldValue.serverTimestamp()));Unit}.await()}
    suspend fun invite(s:SharedSpace,target:String,role:String){checkUser();val e=target.trim();require(e.matches(Regex("[^/~\\s@]+@[^/~\\s@]+\\.[^/~\\s@]+"))&&e!=email);members.document(memberId(s.id,e)).set(mapOf("spaceId" to s.id,"email" to e,"role" to role,"status" to "pending","invitedBy" to uid,"acceptedUid" to "","expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis()+7L*24*60*60*1000)),"updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun respond(m:Membership,accept:Boolean){checkUser();members.document(m.id).update(mapOf("status" to if(accept)"accepted" else "declined","acceptedUid" to if(accept)uid else "","updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun participants(s:SharedSpace):List<Membership>{checkUser();return members.whereEqualTo("spaceId",s.id).get(Source.SERVER).await().documents.map(::membership)}
    suspend fun revoke(m:Membership){checkUser();members.document(m.id).delete().await()}
    suspend fun leave(s:SharedSpace){checkUser();members.document(memberId(s.id,email)).delete().await()}
    suspend fun comments(s:SharedSpace):List<SharedComment>{checkUser();return spaces.document(s.id).collection("comments").orderBy("createdAt").limitToLast(100).get(Source.SERVER).await().documents.map{SharedComment(it.id,it.getString("authorUid").orEmpty(),it.getString("body").orEmpty())}}
    suspend fun comment(s:SharedSpace,body:String){checkUser();require(body.isNotBlank());spaces.document(s.id).collection("comments").add(mapOf("authorUid" to uid,"body" to body.trim(),"createdAt" to FieldValue.serverTimestamp())).await()}
}
