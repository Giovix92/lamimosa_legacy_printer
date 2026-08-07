package com.giovix92.lamimosalegacyprinter.net

import android.content.Context
import com.giovix92.lamimosalegacyprinter.R
import org.conscrypt.Conscrypt
import java.security.KeyStore
import java.security.Security
import java.security.cert.CertificateFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Two separate, independently-confirmed problems on real Jelly Bean 4.1.1
 * hardware, both fixed here:
 *
 * 1) API 16's built-in OpenSSL cannot complete a TLS 1.2 handshake with
 *    Netlify's edge at all (cipher suites/extensions too old), regardless of
 *    which protocol versions are requested on the stock SSLSocketFactory.
 *    Fixed by installing Conscrypt (bundles its own BoringSSL, independent
 *    of the device's system TLS stack).
 *
 * 2) Even with Conscrypt doing the handshake, it defaults to validating the
 *    certificate chain against the *system* CA store - and Jelly Bean's CA
 *    store predates Let's Encrypt entirely. The device (confirmed: Chrome
 *    fails identically) has no trust anchor for ISRG Root X1, so every
 *    Let's Encrypt-issued cert (which is what Netlify serves) fails with
 *    "Trust anchor for certification path not found". Fixed by shipping
 *    ISRG Root X1's certificate ourselves (res/raw/isrg_root_x1.pem, valid
 *    until 2035) and validating against that instead of the OS store.
 */
object TlsSocketFactory {

    // Exposed so RawHttpClient (net/RawHttpClient.kt) can open its own raw
    // sockets through the same Conscrypt engine + pinned trust store, for
    // methods (PATCH/DELETE-with-body) that HttpURLConnection can't send.
    var socketFactory: SSLSocketFactory? = null
        private set

    /** Call once, before the first HTTPS request, e.g. from MainActivity.onCreate(). */
    fun install(context: Context) {
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)

            val cert = context.resources.openRawResource(R.raw.isrg_root_x1).use { input ->
                CertificateFactory.getInstance("X.509").generateCertificate(input)
            }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)
            trustStore.setCertificateEntry("isrg_root_x1", cert)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustStore)

            val sslContext = SSLContext.getInstance("TLS", "Conscrypt")
            sslContext.init(null, tmf.trustManagers, null)
            socketFactory = sslContext.socketFactory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            // Best-effort: if this fails, HTTPS calls fall back to the platform
            // default and will surface their own (broken) handshake/trust error.
        }
    }
}
