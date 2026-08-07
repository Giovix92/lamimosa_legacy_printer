package com.giovix92.lamimosalegacyprinter

import android.app.Application
import android.content.Intent
import com.giovix92.lamimosalegacyprinter.net.TlsSocketFactory
import com.giovix92.lamimosalegacyprinter.service.AlarmScheduler
import com.giovix92.lamimosalegacyprinter.service.OrderCheckService

/**
 * Centralizes what used to be MainActivity-only setup, now that background
 * components (OrderCheckService, triggered by AlarmManager/BootReceiver
 * without the UI ever opening) also need it:
 *
 * - TlsSocketFactory.install() must run before any HTTPS call, from
 *   whichever process component happens to run first.
 * - The polling alarm is (re-)scheduled here too, not just in BootReceiver,
 *   so it's active immediately after a fresh install (no reboot needed) and
 *   self-heals if it was ever cleared (force-stop, alarm dropped by the OS).
 * - An immediate order check is also kicked off here (not just the periodic
 *   alarm). Confirmed on real hardware this matters: AlarmScheduler's first
 *   fire is a full POLL_INTERVAL_MS after scheduling, and OrderCheckService's
 *   first-ever check silently seeds its "known orders" baseline instead of
 *   notifying (by design - otherwise every pre-existing order would fire a
 *   notification the moment this feature ships). Stacked together, an order
 *   created for a quick test right after boot/reinstall could sail straight
 *   into that silent seed pass with nothing to show for it. Seeding
 *   immediately on process start closes that window down to seconds instead
 *   of up to 5 minutes.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TlsSocketFactory.install(this)
        AlarmScheduler.schedule(this)
        startService(Intent(this, OrderCheckService::class.java))
    }
}
