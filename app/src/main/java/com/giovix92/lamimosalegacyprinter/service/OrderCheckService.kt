package com.giovix92.lamimosalegacyprinter.service

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.giovix92.lamimosalegacyprinter.net.OrdersApiClient
import com.giovix92.lamimosalegacyprinter.net.OrdersResult
import com.giovix92.lamimosalegacyprinter.net.TlsSocketFactory

/**
 * Does one GET /api/order, diffs it against the previously-known ID set, and
 * fires a notification for anything new. IntentService already runs its
 * work off the main thread on its own worker thread, which is also exactly
 * what AlarmReceiver needs (BroadcastReceiver.onReceive can't block on
 * network I/O itself).
 */
class OrderCheckService : IntentService("OrderCheckService") {

    override fun onCreate() {
        super.onCreate()
        // Belt-and-braces: this can be the very first component to run in a
        // fresh process (e.g. right after boot, app never opened), so make
        // sure TLS is ready even though Application.onCreate() already does
        // this - install() is cheap and idempotent.
        TlsSocketFactory.install(applicationContext)
    }

    override fun onHandleIntent(intent: Intent?) {
        val prefs = OrderCheckPrefs(applicationContext)

        val result = OrdersApiClient.fetchOrders()
        if (result !is OrdersResult.Success) {
            // Silent failure by design: this runs unattended in the background
            // every few minutes, a transient network blip shouldn't nag anyone.
            // MainActivity's own manual refresh already surfaces real errors.
            Log.w("OrderCheckService", "Background order check failed: ${(result as? OrdersResult.Failure)?.message}")
            return
        }

        val currentIds = result.entries.map { it.order.id }.toSet()

        if (!prefs.seeded) {
            // First run ever (or after data was cleared): don't notify about
            // orders that already existed before this feature was turned on.
            prefs.knownIds = currentIds
            prefs.seeded = true
            return
        }

        val newEntries = result.entries.filter { it.order.id !in prefs.knownIds }
        prefs.knownIds = currentIds

        if (newEntries.isNotEmpty()) {
            NotificationHelper.showNewOrders(
                applicationContext,
                newEntries.size,
                newEntries.firstOrNull()?.order?.name
            )
        }
    }
}
