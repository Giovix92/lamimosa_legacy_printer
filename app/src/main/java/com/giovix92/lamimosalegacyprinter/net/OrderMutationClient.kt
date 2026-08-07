package com.giovix92.lamimosalegacyprinter.net

import com.giovix92.lamimosalegacyprinter.Config
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class MutationResult(val success: Boolean, val errorMessage: String? = null)

/**
 * PATCH/DELETE/POST against the same /api/order endpoint the admin panel
 * uses (order.mjs). PATCH/DELETE go through RawHttpClient, not
 * HttpURLConnection - see net/RawHttpClient.kt for why (PATCH isn't in
 * Android's method whitelist at all; DELETE is whitelisted but rejects a
 * request body, which order.mjs's DELETE handler requires). POST is a
 * normal, fully-supported method, so create() uses plain HttpURLConnection
 * like PrinterClient/OrdersApiClient do.
 */
object OrderMutationClient {

    fun patch(id: String, fields: JSONObject): MutationResult {
        val body = JSONObject(fields.toString())
        body.put("id", id)
        return send("PATCH", body)
    }

    fun delete(id: String): MutationResult {
        val body = JSONObject()
        body.put("id", id)
        return send("DELETE", body)
    }

    /**
     * Creates a new order - mirrors admin.js's "Nuovo Ordine" flow: same
     * fields as PATCH's `fields`, but order.mjs's POST handler reads the
     * event date from `data`, not `eventDate` (an inconsistency in the
     * backend's own field naming between create/update, not something to
     * fix here), and there's no `id` (the server mints one). `silent: true`
     * mirrors the web admin creating an order without pinging its own push
     * subscribers about it. POST needs no Authorization header (order.mjs's
     * POST branch is intentionally public - it's also what the public
     * ordina.js customer flow hits).
     */
    fun create(fields: JSONObject): MutationResult {
        val body = JSONObject(fields.toString())
        val eventDate = body.remove("eventDate")
        if (eventDate != null) body.put("data", eventDate)
        body.put("silent", true)

        return try {
            val url = URL("${Config.ORDERS_API_BASE_URL}/api/order")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }

            val status = conn.responseCode
            if (status in 200..299) {
                conn.disconnect()
                MutationResult(true)
            } else {
                val errorBody = try {
                    BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                } catch (e: Exception) { null }
                conn.disconnect()
                val serverMessage = errorBody?.let { try { JSONObject(it).optString("error").takeIf { m -> m.isNotEmpty() } } catch (e: Exception) { null } }
                MutationResult(false, serverMessage ?: "HTTP $status")
            }
        } catch (e: Exception) {
            MutationResult(false, e.message ?: "Errore di rete")
        }
    }

    private fun send(method: String, body: JSONObject): MutationResult {
        return try {
            val headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${Config.STAFF_PASSWORD}"
            )
            val response = RawHttpClient.request(method, "${Config.ORDERS_API_BASE_URL}/api/order", headers, body.toString())

            if (response.statusCode in 200..299) {
                MutationResult(true)
            } else {
                val serverMessage = try { JSONObject(response.body).optString("error").takeIf { it.isNotEmpty() } } catch (e: Exception) { null }
                MutationResult(false, serverMessage ?: "HTTP ${response.statusCode}")
            }
        } catch (e: Exception) {
            MutationResult(false, e.message ?: "Errore di rete")
        }
    }
}
