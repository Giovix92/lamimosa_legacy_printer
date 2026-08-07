package com.giovix92.lamimosalegacyprinter.util

import android.app.Activity
import android.app.AlertDialog

/**
 * Errors used to be a Toast, which disappears in ~2s and is easy to miss on
 * a busy counter - upgraded to a real dialog that stays until dismissed.
 */
fun Activity.showError(message: String?, title: String = "Errore", onDismiss: (() -> Unit)? = null) {
    if (isFinishing) return
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message ?: "Errore sconosciuto")
        .setPositiveButton("OK") { _, _ -> onDismiss?.invoke() }
        .setCancelable(onDismiss == null)
        .show()
}
