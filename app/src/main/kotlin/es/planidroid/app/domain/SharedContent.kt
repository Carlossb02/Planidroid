package es.planidroid.app.domain
import kotlinx.serialization.Serializable
@Serializable data class SharedLine(val id:String=newId(),val name:String,val done:Boolean=false,val assignee:String="",val due:String="",val amount:Double=1.0,val unit:String="ud",val location:String="",val routine:Boolean=false,val updatedAt:Long=System.currentTimeMillis())
@Serializable data class SharedHistory(val id:String=newId(),val line:SharedLine,val at:Long=System.currentTimeMillis(),val undone:Boolean=false)
@Serializable data class SharedContent(val notes:String="",val lines:List<SharedLine> = emptyList(),val history:List<SharedHistory> = emptyList())

@Serializable data class SharedShopping(val items:List<Shopping> = emptyList(),val history:List<Purchase> = emptyList())
@Serializable data class SharedFurniture(val furniture:Furniture,val items:List<Item> = emptyList())
