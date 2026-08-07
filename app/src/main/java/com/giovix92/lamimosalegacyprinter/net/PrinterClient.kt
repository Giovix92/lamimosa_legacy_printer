package com.giovix92.lamimosalegacyprinter.net

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class PrintResult(val success: Boolean, val httpStatus: Int, val errorMessage: String?)

/**
 * Same protocol as the main app's EpsonFiscalSoapDriver.kt: plain HTTP POST
 * of the fpMate SOAP/XML body to http://{ip}:{port}{endpoint}. Local network
 * only, no TLS involved on this leg.
 */
object PrinterClient {

    fun print(ip: String, port: Int, endpoint: String, xmlPayload: String): PrintResult {
        return try {
            val url = URL("http://$ip:$port$endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "text/xml; charset=UTF-8")

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(xmlPayload) }

            val status = conn.responseCode
            val success = status in 200..299
            conn.disconnect()
            PrintResult(success, status, if (success) null else "La stampante ha risposto con HTTP $status")
        } catch (e: Exception) {
            PrintResult(false, 0, e.message ?: "Errore di rete verso la stampante")
        }
    }
}
