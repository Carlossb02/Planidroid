package es.planidroid.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import es.planidroid.app.data.ReminderWorker
import es.planidroid.app.data.SessionReminderWorker
import es.planidroid.app.data.DailySummaryWorker
import es.planidroid.app.sync.SyncWorker
import es.planidroid.app.sync.CalendarWorker

/** Supports queued WorkManager jobs created before the namespace was renamed. */
class PlanidroidApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(LegacyWorkerFactory()).build()
}

class LegacyWorkerFactory : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            "es.organiza.app.data.ReminderWorker" -> ReminderWorker(appContext, workerParameters)
            "es.organiza.app.data.SessionReminderWorker" -> SessionReminderWorker(appContext, workerParameters)
            "es.organiza.app.data.DailySummaryWorker" -> DailySummaryWorker(appContext, workerParameters)
            "es.organiza.app.sync.SyncWorker" -> SyncWorker(appContext, workerParameters)
            "es.organiza.app.sync.CalendarWorker" -> CalendarWorker(appContext, workerParameters)
            else -> null // WorkManager handles current worker classes normally.
        }
}
