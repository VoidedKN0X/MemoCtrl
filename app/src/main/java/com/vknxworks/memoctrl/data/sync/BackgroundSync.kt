package com.vknxworks.memoctrl.data.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.memoctrl.MainActivity
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.auth.SyncCheck
import com.vknxworks.memoctrl.data.settings.AppSettings
import com.vknxworks.memoctrl.data.settings.SyncInterval
import com.vknxworks.memoctrl.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * One background sync of the signed-in account, the same sync as pull to refresh. It never asks
 * anything: a server that needs its version confirmed is skipped, and so is everything while the app
 * lock has the login encrypted (MemoCtrl is locked). Never retries in a loop; the next run tries again.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.stored.first()
        if (settings.syncInterval == SyncInterval.OFF) return Result.success()
        withTimeoutOrNull(10_000) { c.sessions.ready.first { it } } ?: return Result.success()
        // Locked: the login stays encrypted until you unlock, so there's nothing to sign in with.
        if (c.vault.enabled && !c.vault.isOpen) return Result.success()
        val session = c.sessions.active.value ?: return Result.success()
        if (session.account.isLocal) return Result.success()
        try {
            if (c.sessions.checkSync(automatic = true) is SyncCheck.Allowed) {
                session.repo.sync()
                BackgroundSync.clearFailure(applicationContext)
                Widgets.updateAll(applicationContext)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // The sync badge shows it in the app; the optional notice tells you outside it.
            if (settings.syncFailureNotice) BackgroundSync.notifyFailure(applicationContext)
        }
        return Result.success()
    }
}

object BackgroundSync {
    private const val WORK = "memoctrl-sync"
    private const val CHANNEL = "sync_failed"
    private const val NOTIFICATION_ID = 1001

    /** Schedules (or cancels) the periodic sync to match the settings. Safe to call at every start. */
    fun apply(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (s.syncInterval == SyncInterval.OFF) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (s.syncWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(s.syncCharging)
            .build()
        val req = PeriodicWorkRequestBuilder<SyncWorker>(s.syncInterval.minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Whether MemoCtrl may show notifications (asked when you turn the failure notice on). */
    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** "Background sync failed": no memo text, account or server name, so it's fine on the lock screen. */
    fun notifyFailure(context: Context) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.sync_notice_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.sync_notice_channel_description)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_memoctrl)
            .setContentTitle(context.getString(R.string.sync_notice_title))
            .setContentText(context.getString(R.string.sync_notice_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    fun clearFailure(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}
