package com.giovix92.lamimosalegacyprinter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Fired by AlarmManager every POLL_INTERVAL_MS. Can't do the network call
 * here directly (onReceive must return quickly), so it hands off to
 * OrderCheckService - but a plain startService() call from a receiver has a
 * real gap where the device could fall back asleep before the service gets
 * going. A short timed wake lock (auto-releases itself, no need to plumb a
 * "done" signal back from the IntentService) closes that gap without the
 * complexity of a full WakefulBroadcastReceiver reimplementation.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lamimosa:order-check")
        wakeLock.acquire(30_000L)

        context.startService(Intent(context, OrderCheckService::class.java))
    }
}
