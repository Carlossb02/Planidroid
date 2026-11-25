package es.planidroid.app.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlinx.serialization.Serializable
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Serializable data class SharedSpace(val id:String,val owner:String,val kind:String,val title:String,val body:String,val version:Long,val role:String,val parentId:String="",val homeName:String="",val sourceId:String="")
@Serializable data class Membership(val id:String,val space:String,val email:String,val role:String,val status:String,val acceptedUid:String="",val expiresAt:Long=0)
data class SharedComment(val id:String,val author:String,val body:String)
class SharedCloud(private val context:Context){
    companion object{private val flushMutex=Mutex()}
    private val auth=Cloud.auth(context) ?: error("Configura Firebase primero")
    private val user=auth.currentUser ?: error("Inicia sesión")
    private val uid=user.uid
    private val email=user.email ?: error("La cuenta no tiene correo")
    private val cache=SharedCache(context,uid)
    private val db=FirebaseFirestore.getInstance()
    private val spaces=db.collection("sharedSpaces");private val members=db.collection("memberships")
    private fun checkUser(){check(auth.currentUser?.uid==uid){"La cuenta ha cambiado"}}
    private fun memberId(space:String,email:String)="$space~$email"
    private fun membership(d:com.google.firebase.firestore.DocumentSnapshot)=Membership(d.id,d.getString("spaceId").orEmpty(),d.getString("email").orEmpty(),d.getString("role").orEmpty(),d.getString("status").orEmpty(),d.getString("acceptedUid").orEmpty(),d.getTimestamp("expiresAt")?.toDate()?.time?:0)
    private fun space(d:com.google.firebase.firestore.DocumentSnapshot,role:String)=SharedSpace(d.id,d.getString("ownerUid").orEmpty(),d.getString("kind").orEmpty(),d.getString("title").orEmpty(),d.getString("body").orEmpty(),d.getLong("version")?:0,role,d.getString("parentId").orEmpty(),d.getString("homeName").orEmpty(),d.getString("sourceId").orEmpty())
    fun cached()=cache.visible()
    fun observeCached()=cache.observe()
    fun needsRefresh()=System.currentTimeMillis()-cache.snapshot().refreshed>15*60_000L
    fun pendingEdits()=cache.pending()
    fun discardPending(id:String){checkUser();cache.discard(id)}
    suspend fun list():Pair<List<SharedSpace>,List<Membership>>{
        checkUser()
        if(!Cloud.networkAvailable(context))return cache.visible()
        return try{withTimeoutOrNull(12000){remoteList()}?:cache.visible()}
        catch(e:FirebaseFirestoreException){
            if(e.code==FirebaseFirestoreException.Code.PERMISSION_DENIED){cache.invalidate();throw e}
            if(e.code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.DEADLINE_EXCEEDED))cache.visible()else throw e
        }
    }
    private suspend fun remoteList():Pair<List<SharedSpace>,List<Membership>>{
        checkUser()
        val own=spaces.whereEqualTo("ownerUid",uid).get(Source.SERVER).await().documents.map{space(it,"owner")}
        val mine=members.whereEqualTo("email",email).get(Source.SERVER).await().documents.map(::membership)
        val accepted=mine.filter{it.status=="accepted"&&it.acceptedUid==uid}.mapNotNull{m->
            try{spaces.document(m.space).get(Source.SERVER).await().takeIf{it.exists()}?.let{space(it,m.role)}}
            catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:com.google.firebase.firestore.FirebaseFirestoreException){if(e.code==com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)null else throw e}
        }
        val children=accepted.filter{it.kind=="house"}.flatMap{h->
            spaces.whereEqualTo("ownerUid",h.owner).whereEqualTo("kind","furniture").whereEqualTo("parentId",h.id).get(Source.SERVER).await().documents.map{space(it,h.role)}
        }
        val rank=listOf("reader","editor","admin","owner")
        val visible=(own+accepted+children).groupBy{it.id}.values.map{it.maxBy{v->rank.indexOf(v.role)}}
        checkUser()
        cache.store(visible,mine.filter{it.status=="pending"&&it.expiresAt>System.currentTimeMillis()});return cache.visible()
    }
    private fun stable(kind:String,id:String)=UUID.nameUUIDFromBytes((uid+"/"+kind+"/"+id).toByteArray(Charsets.UTF_8)).toString()
    private fun data(kind:String,title:String,body:String,sourceId:String,parentId:String="",homeName:String=""):Map<String,Any>{
        require(title.isNotBlank()&&title.length<=180&&body.length<=50000)
        return mapOf("ownerUid" to uid,"kind" to kind,"title" to title.trim(),"body" to body,"version" to 1L,"updatedAt" to FieldValue.serverTimestamp(),"sourceId" to sourceId,"parentId" to parentId,"homeName" to homeName.take(180))
    }
    /** Explicit publication: private images and inventory links are excluded. Never overwrite an existing shared resource. */
    suspend fun publishShopping(list:es.planidroid.app.domain.ShoppingList,items:List<es.planidroid.app.domain.Shopping>):SharedSpace {
        checkUser()
        val id=stable("shopping_list",list.id);val ref=spaces.document(id)
        val payload=es.planidroid.app.domain.encode(es.planidroid.app.domain.SharedShopping(items=items.map{it.copy(itemId="",minimum=0.0,target=1.0)}))
        val value=data("shopping_list",list.name,payload,list.id)
        db.runTransaction{tx->if(!tx.get(ref).exists())tx.set(ref,value);Unit}.await()
        return space(ref.get(Source.SERVER).await(),"owner")
    }
    suspend fun publishHome(home:es.planidroid.app.domain.Home,furniture:List<es.planidroid.app.domain.Furniture>,items:List<es.planidroid.app.domain.Item>):SharedSpace {
        checkUser()
        require(furniture.size<=150)
        val id=stable("house",home.id);val parent=spaces.document(id)
        val homeData=data("house",home.name,"{}",home.id)
        val children=furniture.map{f->
            val ref=spaces.document(stable("furniture",f.id))
            val payload=es.planidroid.app.domain.encode(es.planidroid.app.domain.SharedFurniture(f.copy(photo=""),items.filter{it.furnitureId==f.id}.map{it.copy(photo="")}))
            ref to data("furniture",f.name,payload,f.id,id,home.name)
        }
        // Parent is created first; child security checks require an existing owner-controlled parent.
        db.runTransaction{tx->if(!tx.get(parent).exists())tx.set(parent,homeData);Unit}.await()
        if(children.isNotEmpty())db.runTransaction{tx->
            val missing=children.filter{(ref,_)->!tx.get(ref).exists()}
            missing.forEach{(ref,value)->tx.set(ref,value)};Unit
        }.await()
        return space(parent.get(Source.SERVER).await(),"owner")
    }
    suspend fun publishFurniture(home:es.planidroid.app.domain.Home,f:es.planidroid.app.domain.Furniture,items:List<es.planidroid.app.domain.Item>):SharedSpace {
        publishHome(home,listOf(f),items)
        return space(spaces.document(stable("furniture",f.id)).get(Source.SERVER).await(),"owner")
    }
    suspend fun addFurniture(home:SharedSpace,f:es.planidroid.app.domain.Furniture):SharedSpace {
        checkUser();require(home.kind=="house"&&home.role=="owner"&&home.owner==uid)
        val ref=spaces.document(stable("furniture",f.id))
        val payload=es.planidroid.app.domain.encode(es.planidroid.app.domain.SharedFurniture(f.copy(photo=""),emptyList()))
        db.runTransaction{tx->check(!tx.get(ref).exists());tx.set(ref,data("furniture",f.name,payload,f.id,home.id,home.title));Unit}.await()
        return space(ref.get(Source.SERVER).await(),"owner")
    }
    suspend fun create(kind:String,title:String,body:String){checkUser();require(title.isNotBlank());spaces.document(UUID.randomUUID().toString()).set(mapOf("ownerUid" to uid,"kind" to kind,"title" to title.trim(),"body" to body,"version" to 1L,"updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun update(s:SharedSpace,title:String,body:String){
        checkUser();require(Cloud.previouslyApproved(context,uid)&&s.role in listOf("owner","editor","admin"))
        require(title.isNotBlank()&&title.length<=180&&body.length<=50000)
        cache.enqueue(s,title.trim(),body);Cloud.dirtyChanged(context,uid)
    }
    suspend fun flushPending():Int=flushMutex.withLock{
        checkUser()
        var conflicts=0
        for(edit in cache.pending()){
            checkUser()
            try{
                val version=db.runTransaction{tx->
                    val ref=spaces.document(edit.id);val old=tx.get(ref);val current=old.getLong("version")?:-1L
                    if(current>=edit.base+1&&old.getString("title")==edit.title&&old.getString("body")==edit.body)current
                    else if(current!=edit.base)-1L
                    else{tx.update(ref,mapOf("title" to edit.title,"body" to edit.body,"version" to current+1,"updatedAt" to FieldValue.serverTimestamp()));current+1}
                }.await()
                if(version<0)conflicts++ else cache.ack(edit,version)
            }catch(e:FirebaseFirestoreException){
                if(e.code==FirebaseFirestoreException.Code.PERMISSION_DENIED){conflicts++;cache.invalidate()}else throw e
            }
        }
        conflicts
    }
    suspend fun invite(s:SharedSpace,target:String,role:String){checkUser();val e=target.trim();require(e.matches(Regex("[^/~\\s@]+@[^/~\\s@]+\\.[^/~\\s@]+"))&&e!=email);members.document(memberId(s.id,e)).set(mapOf("spaceId" to s.id,"email" to e,"role" to role,"status" to "pending","invitedBy" to uid,"acceptedUid" to "","expiresAt" to com.google.firebase.Timestamp(java.util.Date(System.currentTimeMillis()+7L*24*60*60*1000)),"updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun respond(m:Membership,accept:Boolean){checkUser();members.document(m.id).update(mapOf("status" to if(accept)"accepted" else "declined","acceptedUid" to if(accept)uid else "","updatedAt" to FieldValue.serverTimestamp())).await()}
    suspend fun participants(s:SharedSpace):List<Membership>{checkUser();return members.whereEqualTo("spaceId",s.id).get(Source.SERVER).await().documents.map(::membership)}
    suspend fun revoke(m:Membership){checkUser();members.document(m.id).delete().await()}
    suspend fun leave(s:SharedSpace){checkUser();members.document(memberId(s.id,email)).delete().await()}
    suspend fun comments(s:SharedSpace):List<SharedComment>{checkUser();return spaces.document(s.id).collection("comments").orderBy("createdAt").limitToLast(100).get(Source.SERVER).await().documents.map{SharedComment(it.id,it.getString("authorUid").orEmpty(),it.getString("body").orEmpty())}}
    suspend fun comment(s:SharedSpace,body:String){checkUser();require(body.isNotBlank());spaces.document(s.id).collection("comments").add(mapOf("authorUid" to uid,"body" to body.trim(),"createdAt" to FieldValue.serverTimestamp())).await()}
}
