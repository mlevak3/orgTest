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
    /** Račun je fiskaliziran — CIS je vratio JIR. */
    data class Uspjeh(val jir: String) : FiskalRezultat

    /** CIS je odbio račun s poslovnom greškom (npr. s002 — neispravan potpis). */
    data class Greska(val sifra: String, val poruka: String) : FiskalRezultat

    /**
     * Odgovor je primljen, ali JIR nije pročitan. Račun je MOŽDA fiskaliziran —
     * obavezno provjeriti (ZKI na porezna.gov.hr) prije ponovne fiskalizacije.
     */
    data class Neizvjesno(val poruka: String, val rawOdgovor: String) : FiskalRezultat

    /** Zahtjev nije ni poslan (nema veze, TLS, timeout) ili nema odgovora — sigurno NIJE fiskaliziran. */
    data class Mreza(val poruka: String) : FiskalRezultat
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
    private val extraCaCerts: List<X509Certificate> = emptyList(),
) {
    private val http: OkHttpClient by lazy { buildClient() }

    fun posalji(soapEnvelope: String): FiskalRezultat {
        val request = Request.Builder()
            .url(okolina.url)
            .addHeader("Content-Type", "text/xml; charset=UTF-8")
            .addHeader("SOAPAction", "")
            .post(soapEnvelope.toRequestBody(XML_MEDIA))
            .build()

        // Mrežni sloj: ako ovdje pukne, zahtjev nije obrađen → sigurno nije fiskalizirano.
        val resp = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            return FiskalRezultat.Mreza(opisMrezne(e))
        }

        resp.use {
            val code = it.code
            val text = try { it.body?.string().orEmpty() } catch (e: Exception) {
                return FiskalRezultat.Neizvjesno(
                    "Odgovor je primljen (HTTP $code), ali se nije mogao pročitati: ${e.message}", "",
                )
            }
            if (text.isBlank()) {
                return if (code in 200..299)
                    FiskalRezultat.Neizvjesno("Prazan odgovor poslužitelja (HTTP $code).", "")
                else
                    FiskalRezultat.Mreza("Poslužitelj je vratio HTTP $code bez sadržaja.")
            }
            return parse(text, code)
        }
    }

    private fun parse(xml: String, code: Int): FiskalRezultat {
        // Prvo pokušaj urednog DOM parsiranja; svaki neuspjeh pada na rezervni regex.
        runCatching {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { isExpandEntityReferences = false }
            }
            val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

            firstText(doc.documentElement, "Jir")?.let {
                if (it.isNotBlank()) return FiskalRezultat.Uspjeh(it)
            }
            firstElement(doc.documentElement, "Greska")?.let { g ->
                return FiskalRezultat.Greska(
                    firstText(g, "SifraGreske").orEmpty(),
                    firstText(g, "PorukaGreske").orEmpty(),
                )
            }
            firstText(doc.documentElement, "faultstring")?.let {
                return FiskalRezultat.Greska("SOAP-Fault", it)
            }
        }

        // Rezervno: izvuci JIR/grešku regexom čak i ako je XML neispravan.
        jirRegex.find(xml)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return FiskalRezultat.Uspjeh(it) }
        val sifra = sifraRegex.find(xml)?.groupValues?.get(1)
        val poruka = porukaRegex.find(xml)?.groupValues?.get(1)
        if (sifra != null || poruka != null) {
            return FiskalRezultat.Greska(sifra.orEmpty(), poruka.orEmpty())
        }

        // Odgovor je stigao, ali JIR/grešku nismo prepoznali → NEIZVJESNO.
        return FiskalRezultat.Neizvjesno(
            "Odgovor primljen (HTTP $code) ali JIR nije prepoznat. Račun je MOŽDA fiskaliziran.",
            xml.take(2000),
        )
    }

    private fun opisMrezne(e: Exception): String = when (e) {
        is javax.net.ssl.SSLHandshakeException ->
            "TLS: certifikat poslužitelja nije prihvaćen (učitaj FINA CA u Postavkama). ${e.message}"
        is javax.net.ssl.SSLException -> "TLS greška: ${e.message}"
        is java.net.UnknownHostException -> "Nema internetske veze ili je poslužitelj nedostupan."
        is java.net.SocketTimeoutException -> "Isteklo vrijeme čekanja (timeout). Pokušaj ponovno."
        is java.net.ConnectException -> "Nije se moguće spojiti na poslužitelj fiskalizacije."
        else -> e.message ?: e.javaClass.simpleName
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
        } else {
            // Sistemski CA + (opcionalno) uvezeni FINA CA certifikati.
            val tm = CompositeX509TrustManager(extraCaCerts)
            builder.sslSocketFactory(TlsTrust.sslContext(tm).socketFactory, tm)
        }
        return builder.build()
    }

    companion object {
        private val XML_MEDIA = "text/xml; charset=utf-8".toMediaType()
        private val jirRegex = Regex("<(?:\\w+:)?Jir>([^<]+)</")
        private val sifraRegex = Regex("<(?:\\w+:)?SifraGreske>([^<]+)</")
        private val porukaRegex = Regex("<(?:\\w+:)?PorukaGreske>([^<]+)</")
    }
}
