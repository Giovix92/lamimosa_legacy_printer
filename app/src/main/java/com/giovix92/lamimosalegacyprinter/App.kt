package com.giovix92.lamimosalegacyprinter

import android.app.Application
import com.giovix92.lamimosalegacyprinter.net.TlsSocketFactory
import com.giovix92.lamimosalegacyprinter.service.AlarmScheduler

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
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TlsSocketFactory.install(this)
        AlarmScheduler.schedule(this)
    }
}
