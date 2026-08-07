package com.giovix92.lamimosalegacyprinter.service

import android.content.Context

/**
 * Tracks which order IDs have already been seen/notified about, across
 * process restarts (SharedPreferences, not in-memory - the whole point of
 * this feature is to work when the app was never opened, e.g. right after
 * boot).
 */
class OrderCheckPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("order_check", Context.MODE_PRIVATE)

    // Whether we've ever completed a check. The very first check after
    // install just seeds knownIds silently - otherwise every pre-existing
    // order in the backend would fire a notification the first time the
    // service ever runs.
    var seeded: Boolean
        get() = prefs.getBoolean("seeded", false)
        set(value) = prefs.edit().putBoolean("seeded", value).apply()

    var knownIds: Set<String>
        get() = prefs.getStringSet("known_ids", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("known_ids", value).apply() }
}
