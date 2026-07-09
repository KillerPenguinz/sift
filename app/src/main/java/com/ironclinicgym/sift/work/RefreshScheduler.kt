package com.ironclinicgym.sift.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ironclinicgym.sift.core.domain.SyncRules
import java.util.concurrent.TimeUnit

/**
 * Schedules the pull refresh: a unique periodic job at the WorkManager floor (~15 min) plus a
 * one-shot for manual refresh. Both require network. The policy values come from core
 * [SyncRules] so the cadence stays a business decision, not a UI one.
 */
object RefreshScheduler {
    private const val PERIODIC_NAME = "sift_periodic_refresh"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensurePeriodic(context: Context) {
        Log.d("BatteryDebug", "ensurePeriodic: scheduling every ${SyncRules.PERIODIC_REFRESH_MINUTES}m")
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(
            SyncRules.PERIODIC_REFRESH_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    fun refreshNow(context: Context) {
        Log.d("BatteryDebug", "refreshNow: enqueuing one-shot refresh")
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
