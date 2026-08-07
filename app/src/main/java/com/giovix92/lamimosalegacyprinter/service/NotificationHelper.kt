package com.giovix92.lamimosalegacyprinter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.giovix92.lamimosalegacyprinter.MainActivity
import com.giovix92.lamimosalegacyprinter.R

private const val CHANNEL_ID = "new_orders"
private const val NOTIFICATION_ID = 1001

object NotificationHelper {

    /**
     * NotificationChannel is API 26+ only - guarded by an SDK check, not a
     * problem for a minSdk-16 app: Dalvik verifies methods referencing
     * classes unavailable on the running OS lazily (at first execution of
     * that code path, not at load time), so this branch never runs - and
     * never gets verified - on Jelly Bean. Standard "one APK targets many
     * API levels" pattern.
     */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(CHANNEL_ID, "Nuovi ordini", NotificationManager.IMPORTANCE_HIGH)
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showNewOrders(context: Context, count: Int, singleOrderName: String?) {
        ensureChannel(context)

        val title = if (count == 1) "Nuovo ordine" else "$count nuovi ordini"
        val text = if (count == 1 && !singleOrderName.isNullOrEmpty()) {
            singleOrderName
        } else {
            "Tocca per vedere gli ordini in attesa"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        // The single-arg Notification.Builder(Context) constructor is
        // deprecated (API 26 wants the channel-aware one) but is the only
        // one that exists on API 16 - the deprecation is a compile-time
        // nag about newer OSes, not something that breaks on this one.
        @Suppress("DEPRECATION")
        val builder = Notification.Builder(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID)
        }

        // Notification.Builder.build() has existed since API 16 (this app's own
        // minSdk floor), so there's no older device to fall back to here.
        val notification: Notification = builder.build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
