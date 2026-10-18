package com.codetrio.spatialflow.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.codetrio.spatialflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val updateManager = UpdateManager(applicationContext)
        if (updateManager.isInstalledFromFdroid()) {
            Log.d(TAG, "Periodic update check skipped (managed by F-Droid)")
            return@withContext Result.success()
        }

        if (prefs.getBoolean("update_check_disabled", false)) {
            Log.d(TAG, "Periodic update check skipped (disabled by user)")
            return@withContext Result.success()
        }

        try {
            val client = GitHubReleaseClient("MythicalSHUB", "SpatialFlow")
            val release = client.latestRelease
            if (release == null) {
                Log.w(TAG, "Failed to retrieve latest release info from GitHub")
                return@withContext Result.retry()
            }

            val currentVersion = BuildConfig.VERSION_NAME
            if (VersionUtils.isNewer(release.tagName, currentVersion)) {
                Log.i(TAG, "New version found: ${release.tagName} (current: $currentVersion). Posting notification.")
                val updateManager = UpdateManager(applicationContext)
                updateManager.showUpdateNotification(
                    tagName = release.tagName,
                    changelog = release.changelog ?: "",
                    apkUrl = release.apkUrl ?: ""
                )
            } else {
                Log.d(TAG, "App is up to date (current: $currentVersion, latest: ${release.tagName})")
            }

            prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic update check", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        const val WORK_NAME = "spatialflow_periodic_update_check"

        fun schedulePeriodicCheck(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
                Log.d(TAG, "Scheduled 24h periodic background update check")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule periodic update check", e)
            }
        }
    }
}
