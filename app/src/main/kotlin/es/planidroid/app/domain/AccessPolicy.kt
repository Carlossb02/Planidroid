package es.planidroid.app.domain
/** First grant must come from server; offline use is limited to a previously verified UID. */
object AccessPolicy{
    fun allowed(uid:String,previous:Boolean,server:Boolean?):Boolean=uid!="local"&&uid.isNotBlank()&&(server==true||(server==null&&previous))
}
