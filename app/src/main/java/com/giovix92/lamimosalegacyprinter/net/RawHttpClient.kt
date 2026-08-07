package com.giovix92.lamimosalegacyprinter.net

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.util.Locale
import javax.net.ssl.SSLSocket

data class RawResponse(val statusCode: Int, val body: String)

/**
 * java.net.HttpURLConnection on Android hard-rejects two things we need:
 *
 * 1) PATCH isn't in its internal method whitelist at all -
 *    setRequestMethod("PATCH") throws ProtocolException("Invalid HTTP
 *    method: PATCH") - confirmed on real hardware.
 * 2) DELETE is whitelisted but treated as a body-less method - setting
 *    doOutput=true and writing to it throws
 *    ProtocolException("DELETE does not support writing") - also confirmed
 *    on real hardware. order.mjs's DELETE handler requires a JSON body
 *    ({"id": ...}), so this isn't optional.
 *
 * Both restrictions live entirely inside HttpURLConnection's Java-level
 * method validation, not in HTTP itself or in the TLS layer - any method
 * name is valid on the wire. So instead of fighting HttpURLConnection with
 * version-fragile reflection hacks, this opens the TLS socket directly
 * (through the same Conscrypt engine + pinned trust store as
 * TlsSocketFactory - see net/TlsCompat.kt) and writes the HTTP/1.1 request
 * by hand, then parses the response per RFC 7230 (Content-Length or
 * chunked, whichever the server sends - Netlify's edge may not honor our
 * "Connection: close" hint, so this doesn't just block-read until EOF).
 * Only used for PATCH/DELETE; GET (OrdersApiClient) and POST
 * (PrinterClient) work fine through the normal HttpURLConnection/
 * HttpsURLConnection APIs and are left alone.
 */
object RawHttpClient {

    fun request(method: String, urlString: String, headers: Map<String, String>, body: String?): RawResponse {
        val url = URL(urlString)
        val host = url.host
        val port = if (url.port != -1) url.port else 443
        val path = (if (url.path.isNullOrEmpty()) "/" else url.path) + (url.query?.let { "?$it" } ?: "")

        val factory = TlsSocketFactory.socketFactory
            ?: throw IllegalStateException("TlsSocketFactory.install() non è stato chiamato")

        val socket = factory.createSocket(host, port) as SSLSocket
        socket.soTimeout = 15000
        try {
            socket.startHandshake()

            val bodyBytes = body?.toByteArray(Charsets.UTF_8)
            val requestHeaders = StringBuilder()
            requestHeaders.append("$method $path HTTP/1.1\r\n")
            requestHeaders.append("Host: $host\r\n")
            headers.forEach { (k, v) -> requestHeaders.append("$k: $v\r\n") }
            if (bodyBytes != null) requestHeaders.append("Content-Length: ${bodyBytes.size}\r\n")
            requestHeaders.append("Connection: close\r\n")
            requestHeaders.append("\r\n")

            val out = BufferedOutputStream(socket.outputStream)
            out.write(requestHeaders.toString().toByteArray(Charsets.UTF_8))
            if (bodyBytes != null) out.write(bodyBytes)
            out.flush()

            return readResponse(socket.inputStream)
        } finally {
            try { socket.close() } catch (e: Exception) { /* best-effort */ }
        }
    }

    private fun readResponse(input: InputStream): RawResponse {
        val statusLine = readLine(input) ?: return RawResponse(-1, "")
        val statusCode = Regex("""HTTP/1\.[01]\s+(\d+)""").find(statusLine)?.groupValues?.get(1)?.toIntOrNull() ?: -1

        val headerMap = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headerMap[line.substring(0, idx).trim().lowercase(Locale.US)] = line.substring(idx + 1).trim()
        }

        val contentLength = headerMap["content-length"]?.toIntOrNull()
        val chunked = headerMap["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true

        val bodyBytes = when {
            chunked -> readChunkedBody(input)
            contentLength != null -> readExactly(input, contentLength)
            else -> input.readBytes() // no length given: read until the server closes
        }

        return RawResponse(statusCode, String(bodyBytes, Charsets.UTF_8))
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) {
                val bytes = buf.toByteArray()
                val len = if (bytes.isNotEmpty() && bytes[bytes.size - 1] == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.ISO_8859_1)
            }
            buf.write(b)
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n == -1) break
            read += n
        }
        return if (read == length) buf else buf.copyOf(read)
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.trim().substringBefore(';').toIntOrNull(16) ?: break
            if (size == 0) {
                readLine(input) // trailing blank line after the terminating 0-chunk
                break
            }
            out.write(readExactly(input, size))
            readLine(input) // CRLF after each chunk's data
        }
        return out.toByteArray()
    }
}
