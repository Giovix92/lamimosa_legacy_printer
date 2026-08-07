package com.giovix92.lamimosalegacyprinter.net

import android.content.Context

/** Plain SharedPreferences (API 1+) — mirrors the fields on the main app's PrinterEntity. */
class PrinterPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE)

    var ip: String
        get() = prefs.getString(KEY_IP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IP, value).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 80)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, "/cgi-bin/fpmate.cgi") ?: "/cgi-bin/fpmate.cgi"
        set(value) = prefs.edit().putString(KEY_ENDPOINT, value).apply()

    var operatorId: String
        get() = prefs.getString(KEY_OPERATOR, "1") ?: "1"
        set(value) = prefs.edit().putString(KEY_OPERATOR, value).apply()

    var maxLineChars: Int
        get() = prefs.getInt(KEY_MAX_LINE_CHARS, 32)
        set(value) = prefs.edit().putInt(KEY_MAX_LINE_CHARS, value).apply()

    fun isConfigured(): Boolean = ip.isNotEmpty()

    companion object {
        private const val KEY_IP = "ip"
        private const val KEY_PORT = "port"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_OPERATOR = "operator_id"
        private const val KEY_MAX_LINE_CHARS = "max_line_chars"
    }
}
