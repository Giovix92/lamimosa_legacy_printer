package com.giovix92.lamimosalegacyprinter.net

import com.giovix92.lamimosalegacyprinter.Config
import com.giovix92.lamimosalegacyprinter.model.Order
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Order paired with its raw JSON - detail/edit screens PATCH against the raw
 *  object so unknown/legacy fields this app doesn't model are preserved. */
data class OrderEntry(val order: Order, val json: JSONObject)

sealed class OrdersResult {
    data class Success(val entries: List<OrderEntry>) : OrdersResult()
    data class Failure(val message: String) : OrdersResult()
}

/**
 * GET /api/order — same endpoint/auth scheme as order.mjs's checkAuth():
 * "Authorization: Bearer <STAFF_PASSWORD>". TlsSocketFactory.install() must
 * have been called once beforehand (see MainActivity) or this handshake
 * will fail on API 16's default-disabled TLS 1.2.
 */
object OrdersApiClient {

    fun fetchOrders(): OrdersResult {
        return try {
            val url = URL("${Config.ORDERS_API_BASE_URL}/api/order")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", "Bearer ${Config.STAFF_PASSWORD}")

            val status = conn.responseCode
            if (status !in 200..299) {
                conn.disconnect()
                return OrdersResult.Failure("HTTP $status dal server ordini")
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()

            val array = JSONArray(body)
            val entries = ArrayList<OrderEntry>(array.length())
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                entries.add(OrderEntry(Order.fromJson(json), json))
            }
            OrdersResult.Success(entries)
        } catch (e: Exception) {
            OrdersResult.Failure(e.message ?: "Errore di rete verso il server ordini")
        }
    }
}
