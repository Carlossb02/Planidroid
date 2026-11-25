package es.planidroid.app.data

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.work.*
import es.planidroid.app.MainActivity
import es.planidroid.app.domain.*
import es.planidroid.app.sync.Cloud
import java.time.*
import java.util.concurrent.TimeUnit

object Reminders {
    fun cancel(context:Context,scope:String,id:String)=WorkManager.getInstance(context).cancelUniqueWork("reminder-$scope-$id")
    fun update(context: Context,scope: String,t: Task) {
        val wm=WorkManager.getInstance(context); val key="reminder-$scope-${t.id}"
        if(!t.reminder || t.due.isBlank() || t.status in listOf(Status.DONE,Status.ARCHIVED)) { wm.cancelUniqueWork(key); return }
        val whenMs=LocalDate.parse(t.due).minusDays(t.reminderBeforeDays.toLong()).atTime(LocalTime.parse(t.reminderTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(whenMs <= System.currentTimeMillis()) { wm.cancelUniqueWork(key); return }
        wm.enqueueUniqueWork(key,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(whenMs-System.currentTimeMillis(),TimeUnit.MILLISECONDS).setInputData(workDataOf("scope" to scope,"id" to t.id)).build())
    }
}
class ReminderWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val scope=inputData.getString("scope") ?: return Result.failure(); val id=inputData.getString("id") ?: return Result.failure()
        val active=Cloud.auth(applicationContext)?.currentUser?.uid ?: "local"
        if(active != scope || NotificationPrefs.muted(applicationContext,scope)) return Result.success()
        val r=PlanidroidDb.get(applicationContext).records().get(scope,id) ?: return Result.success()
        if(r.deleted) return Result.success()
        val t=decode<Task>(r.payload)
        if(!t.reminder || t.status in listOf(Status.DONE,Status.ARCHIVED)) return Result.success()
        val manager=applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tasks","Recordatorios",NotificationManager.IMPORTANCE_DEFAULT))
        val pending=PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if(manager.areNotificationsEnabled()) runCatching { manager.notify(id.hashCode(),Notification.Builder(applicationContext,"tasks").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(t.title).setContentText("Fecha límite: ${t.due}").setContentIntent(pending).setAutoCancel(true).build()) }
        return Result.success()
    }
}
