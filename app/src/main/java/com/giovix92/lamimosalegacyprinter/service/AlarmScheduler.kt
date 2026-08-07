package com.giovix92.lamimosalegacyprinter.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

private const val REQUEST_CODE = 4001
private val POLL_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

/**
 * No WorkManager/JobScheduler here (both API 21+, off-limits at minSdk 16) -
 * AlarmManager is the only cross-API-level way to run something
 * periodically in the background, including across reboots (paired with
 * BootReceiver, since alarms don't survive a reboot on their own).
 */
object AlarmScheduler {

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact: lets the OS batch this with other alarms to save battery -
        // a 5-minute polling interval doesn't need to be exact-to-the-second.
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + POLL_INTERVAL_MS,
            POLL_INTERVAL_MS,
            pendingIntent(context)
        )
    }
}
