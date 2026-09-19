package com.p67906265.meteoapp

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WeatherWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        WeatherWidgetProvider.refreshAll(applicationContext)
        ForecastWidgetProvider.refreshAll(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

object WeatherWidgetRefreshScheduler {
    private const val PERIODIC_WORK = "meteo_widget_refresh_periodic"
    private const val IMMEDIATE_WORK = "meteo_widget_refresh_immediate"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WeatherWidgetRefreshWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWidgetRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
