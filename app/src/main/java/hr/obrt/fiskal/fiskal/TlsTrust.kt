package hr.obrt.fiskal.fiskal

import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Pomoćnici za TLS povjerenje. Produkcijski CIS poslužitelj koristi certifikat
 * izdan od FINA RDC 2020 CA (potpisan FINA Root CA). Budući da taj lanac nije
 * uvijek u Android trust storeu, korisnik može uvesti FINA CA certifikat(e), a
 * aplikacija im vjeruje uz sistemske CA.
 */
object TlsTrust {

    fun parseCertificates(input: InputStream): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return input.use { cf.generateCertificates(it).filterIsInstance<X509Certificate>() }
    }

    fun systemTrustManager(): X509TrustManager = trustManagerFromKeyStore(null)

    fun trustManagerForCerts(certs: List<X509Certificate>): X509TrustManager {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        certs.forEachIndexed { i, c -> ks.setCertificateEntry("ca$i", c) }
        return trustManagerFromKeyStore(ks)
    }

    private fun trustManagerFromKeyStore(ks: KeyStore?): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    fun sslContext(trustManager: X509TrustManager): SSLContext {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        return ctx
    }
}

/**
 * Vjeruje certifikatu ako ga prihvati sistemski trust store ILI skup dodatnih
 * (npr. uvezenih FINA) CA certifikata.
 */
class CompositeX509TrustManager(extraCerts: List<X509Certificate>) : X509TrustManager {

    private val system = TlsTrust.systemTrustManager()
    private val extra = if (extraCerts.isNotEmpty()) TlsTrust.trustManagerForCerts(extraCerts) else null

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try {
            system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            extra?.checkServerTrusted(chain, authType) ?: throw e
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        system.acceptedIssuers + (extra?.acceptedIssuers ?: emptyArray())
}

/** Učitava dodatne CA certifikate: ugrađene (res/raw/fina_ca, opcionalno) + korisnički uvezene. */
object CaStore {
    fun loadExtraCas(context: Context, userCaBytes: ByteArray?): List<X509Certificate> {
        val result = mutableListOf<X509Certificate>()
        val resId = context.resources.getIdentifier("fina_ca", "raw", context.packageName)
        if (resId != 0) {
            runCatching { context.resources.openRawResource(resId).use { result += TlsTrust.parseCertificates(it) } }
        }
        userCaBytes?.let { runCatching { result += TlsTrust.parseCertificates(it.inputStream()) } }
        return result
    }
}
