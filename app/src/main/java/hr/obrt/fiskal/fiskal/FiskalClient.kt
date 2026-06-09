package hr.obrt.fiskal.fiskal

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Okolina fiskalizacije. */
enum class FiskalOkolina(val url: String, val opis: String) {
    TEST("https://cistest.apis-it.hr:8449/FiskalizacijaServiceTest", "Testna (DEMO certifikat)"),
    PRODUKCIJA("https://cis.porezna-uprava.hr:8449/FiskalizacijaService", "Produkcija (FINA certifikat)"),
}

/** Rezultat fiskalizacije računa. */
sealed interface FiskalRezultat {
    data class Uspjeh(val jir: String) : FiskalRezultat
    /** Greška koju je vratio CIS (npr. s002 — neispravan potpis). */
    data class Greska(val sifra: String, val poruka: String) : FiskalRezultat
    /** Mrežna/TLS/ostala lokalna greška. */
    data class Iznimka(val poruka: String) : FiskalRezultat
}

/**
 * Šalje potpisani `RacunZahtjev` na CIS i parsira `RacunOdgovor`.
 *
 * @param ignoreTlsTrust SAMO za TEST okolinu — preskače provjeru lanca povjerenja
 *        jer demo poslužitelj koristi FINA DEMO CA koji nije u Android trust storeu.
 *        NIKAD ne uključivati za PRODUKCIJU.
 */
class FiskalClient(
    private val okolina: FiskalOkolina,
    private val ignoreTlsTrust: Boolean = false,
) {
    private val http: OkHttpClient by lazy { buildClient() }

    fun posalji(soapEnvelope: String): FiskalRezultat {
        return try {
            val body = soapEnvelope.toRequestBody(XML_MEDIA)
            val request = Request.Builder()
                .url(okolina.url)
                .addHeader("Content-Type", "text/xml; charset=UTF-8")
                .addHeader("SOAPAction", "")
                .post(body)
                .build()

            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                parse(text)
            }
        } catch (e: Exception) {
            FiskalRezultat.Iznimka(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parse(xml: String): FiskalRezultat {
        if (xml.isBlank()) return FiskalRezultat.Iznimka("Prazan odgovor poslužitelja.")
        return try {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val doc = dbf.newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

            firstText(doc.documentElement, "Jir")?.let {
                if (it.isNotBlank()) return FiskalRezultat.Uspjeh(it)
            }

            val greska = firstElement(doc.documentElement, "Greska")
            if (greska != null) {
                val sifra = firstText(greska, "SifraGreske").orEmpty()
                val poruka = firstText(greska, "PorukaGreske").orEmpty()
                return FiskalRezultat.Greska(sifra, poruka)
            }

            val fault = firstText(doc.documentElement, "faultstring")
            if (fault != null) return FiskalRezultat.Greska("SOAP-Fault", fault)

            FiskalRezultat.Iznimka("Neprepoznat odgovor:\n${xml.take(500)}")
        } catch (e: Exception) {
            FiskalRezultat.Iznimka("Greška pri čitanju odgovora: ${e.message}")
        }
    }

    private fun firstElement(root: Element, localName: String): Element? {
        val nodes = root.getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0) as Element else null
    }

    private fun firstText(root: Element, localName: String): String? =
        firstElement(root, localName)?.textContent?.trim()

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (ignoreTlsTrust && okolina == FiskalOkolina.TEST) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(trustAll), java.security.SecureRandom())
            builder.sslSocketFactory(ctx.socketFactory, trustAll)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    companion object {
        private val XML_MEDIA = "text/xml; charset=utf-8".toMediaType()
    }
}
