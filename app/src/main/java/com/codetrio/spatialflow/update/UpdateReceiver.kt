package com.codetrio.spatialflow.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class UpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "UpdateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE != intent.action) {
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val savedDownloadId = prefs.getLong("download_id", -1)

        if (downloadId != savedDownloadId) {
            return // Not our download
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val uriString = cursor.getString(uriIndex)

                if (uriString != null) {
                    val path = Uri.parse(uriString).path
                    if (path != null) {
                        val apkFile = File(path)
                        installApk(context, apkFile)
                    }
                }
            }
        }
        cursor.close()
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // CRITICAL
            }

            // Check install permission for Android 8+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "Cannot request package installs permission directly; posting install notification")
                    showInstallNotification(context, installIntent)
                    return
                }
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK directly", e)
        }
    }

    private fun showInstallNotification(context: Context, installIntent: Intent) {
        try {
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                1001,
                installIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "spatialflow_updates",
                    "App Updates",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when an app update is ready to install"
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(channel)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(context, "spatialflow_updates")
                .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_applogo)
                .setContentTitle("Update Downloaded")
                .setContentText("Tap to install the latest version of SpatialFlow.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setShowWhen(true)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(998, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post install notification", e)
        }
    }
}
