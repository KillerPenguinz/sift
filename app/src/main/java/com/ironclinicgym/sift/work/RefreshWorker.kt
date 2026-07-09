package com.ironclinicgym.sift.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ironclinicgym.sift.SiftApp
import com.ironclinicgym.sift.data.repository.SiftRepository.RefreshResult

/**
 * Periodic + on-demand pull refresh. Reads the manual DI container from the Application (no
 * Hilt worker injection needed). Transient failures retry; auth loss does not loop forever
 * (the UI surfaces the reconnect path instead).
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.d("BatteryDebug", "RefreshWorker.doWork attempt=${runAttemptCount}")
        val repository = (applicationContext as SiftApp).container.repository
        return when (val r = repository.refresh()) {
            is RefreshResult.Success -> {
                Log.d("BatteryDebug", "RefreshWorker success, ${r.count} tasks")
                Result.success()
            }
            RefreshResult.NoMapping -> {
                Log.d("BatteryDebug", "RefreshWorker no mapping, skipping")
                Result.success()
            }
            RefreshResult.NeedsReconnect -> {
                Log.d("BatteryDebug", "RefreshWorker needs reconnect, stopping retries")
                Result.success()
            }
            is RefreshResult.Failed -> {
                if (runAttemptCount >= 3) {
                    Log.w("BatteryDebug", "RefreshWorker giving up after $runAttemptCount attempts: ${r.message}")
                    Result.failure()
                } else {
                    Log.d("BatteryDebug", "RefreshWorker failed (attempt $runAttemptCount), will retry: ${r.message}")
                    Result.retry()
                }
            }
        }
    }
}
