package es.planidroid.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="records", primaryKeys=["scope","id"])
data class Record(val scope: String, val id: String, val kind: String, val payload: String, val deleted: Boolean = false, val dirty: Boolean = true, val baseVersion: Long = 0, val revision: Long = 1)
@Dao interface RecordDao {
    @Query("SELECT * FROM records WHERE scope=:scope AND deleted=0 ORDER BY id") fun observe(scope: String): Flow<List<Record>>
    @Query("DELETE FROM records WHERE scope=:scope") suspend fun clearScope(scope:String)
    @Query("SELECT * FROM records WHERE scope=:scope") suspend fun all(scope: String): List<Record>
    @Query("SELECT * FROM records WHERE scope=:scope AND id=:id") suspend fun get(scope: String, id: String): Record?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(record: Record)
    @Query("UPDATE records SET baseVersion=:version, dirty=CASE WHEN revision=:revision THEN 0 ELSE 1 END WHERE scope=:scope AND id=:id") suspend fun ack(scope: String,id: String,revision: Long,version: Long)
    @Transaction suspend fun save(scope: String,id: String,kind: String,payload: String,deleted: Boolean = false) {
        val old = get(scope,id)
        put(Record(scope,id,kind,payload,deleted,true,old?.baseVersion ?: 0,(old?.revision ?: 0)+1))
    }
    @Transaction suspend fun remove(scope: String,id: String) { get(scope,id)?.let { save(scope,id,it.kind,it.payload,true) } }
    @Transaction suspend fun acceptRemote(record: Record) { if (get(record.scope,record.id)?.dirty != true) put(record) }
}
@Database(entities=[Record::class],version=1,exportSchema=false)
abstract class PlanidroidDb : RoomDatabase() {
    abstract fun records(): RecordDao
    companion object {
        // Keep the old physical SQLite file only for existing installations, including WAL data.
        internal fun databaseName(context: Context): String = when {
            context.getDatabasePath("planidroid.db").exists() -> "planidroid.db"
            context.getDatabasePath("organiza.db").exists() -> "organiza.db"
            else -> "planidroid.db"
        }
        @Volatile private var instance: PlanidroidDb? = null
        fun get(context: Context): PlanidroidDb = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext,PlanidroidDb::class.java,databaseName(context)).build().also { instance=it } }
    }
}
