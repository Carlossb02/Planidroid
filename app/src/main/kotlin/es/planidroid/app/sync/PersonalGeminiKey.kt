package es.planidroid.app.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per installation and UID. Ciphertext only, in noBackupFilesDir; key never enters Room, backups or Firestore. */
object PersonalGeminiKey {
    private fun alias(uid:String):String {
        require(uid!="local"&&uid.isNotBlank())
        return "planidroid.gemini."+MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    private fun file(ctx:Context,uid:String)=AtomicFile(File(ctx.noBackupFilesDir,alias(uid)+".bin"))
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    private fun key(uid:String,create:Boolean):SecretKey {
        val name=alias(uid)
        val existing=store().getKey(name,null) as? SecretKey
        if(existing!=null)return existing
        check(create){"Vuelve a introducir tu clave personal"}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(name,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun save(ctx:Context,uid:String,secret:String){
        require(Cloud.auth(ctx)?.currentUser?.uid==uid)
        require(secret.length in 20..512&&secret.none{it.isWhitespace()||it.isISOControl()}){"Clave API no válida"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(uid,true));cipher.updateAAD(alias(uid).toByteArray())
        val plain=secret.toByteArray(Charsets.UTF_8)
        val bytes=try{cipher.doFinal(plain)}finally{plain.fill(0)}
        val out=file(ctx,uid);val stream=out.startWrite()
        try{stream.write(byteArrayOf(cipher.iv.size.toByte()));stream.write(cipher.iv);stream.write(bytes);out.finishWrite(stream)}
        catch(e:Exception){out.failWrite(stream);throw e}
        ServiceHealth.mark(ctx,uid,"gemini",false)
    }
    @Synchronized fun load(ctx:Context,uid:String):String{
        require(Cloud.auth(ctx)?.currentUser?.uid==uid)
        val data=file(ctx,uid).readFully();val n=data[0].toInt() and 255
        require(n==12&&data.size>n+17&&data.size<=1024)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(uid,false),GCMParameterSpec(128,data.copyOfRange(1,n+1)))
        cipher.updateAAD(alias(uid).toByteArray())
        val plain=cipher.doFinal(data.copyOfRange(n+1,data.size))
        return try{plain.toString(Charsets.UTF_8)}finally{plain.fill(0)}
    }
    fun configured(ctx:Context,uid:String)=uid!="local"&&runCatching{file(ctx,uid).baseFile.exists()&&store().containsAlias(alias(uid))}.getOrDefault(false)
    @Synchronized fun remove(ctx:Context,uid:String){if(uid=="local")return;file(ctx,uid).delete();store().deleteEntry(alias(uid));ServiceHealth.mark(ctx,uid,"gemini",false)}
}
