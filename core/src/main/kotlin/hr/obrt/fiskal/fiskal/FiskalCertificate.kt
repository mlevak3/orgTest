package hr.obrt.fiskal.fiskal

import java.io.InputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Učitani FINA aplikacijski certifikat (.p12 / .pfx) s privatnim ključem.
 *
 * Drži privatni ključ (za potpisivanje ZKI-ja i XML-a) te X.509 certifikat
 * (za KeyInfo u XML potpisu).
 */
class FiskalCertificate private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
    /**
     * Cijeli lanac certifikata iz .p12 (krajnji/leaf certifikat prvi, pa
     * međucertifikat(i)) — ako ga PKCS#12 sadrži. Šalje se u KeyInfo XML potpisa
     * kako bi CIS mogao izgraditi put povjerenja i kad u svom trust storeu nema
     * baš taj (npr. stariji demo) međucertifikat. Ako .p12 ima samo leaf, lanac
     * je jednočlan.
     */
    val chain: List<X509Certificate>,
) {
    /** OIB iz certifikata (zadnjih 11 znamenki iz subject-a), ako se može pročitati. */
    val oibIzCertifikata: String? by lazy {
        // FINA aplikacijski certifikati u CN/serialNumber sadrže OIB obveznika.
        Regex("\\d{11}").find(certificate.subjectX500Principal.name)?.value
    }

    /**
     * Naziv izdavatelja (CA) certifikata — koristan za dijagnostiku: DEMO certifikat
     * mora biti izdan od FINA DEMO CA da bi ga testni CIS (cistest) prihvatio; ako
     * je učitan produkcijski certifikat (FINA RDC 2020 i sl.), CIS testna okolina
     * ga odbija s greškom o neispravnom potpisu iako je matematički potpis ispravan.
     */
    val izdavatelj: String get() = certificate.issuerX500Principal.name

    /** Je li certifikat trenutno u razdoblju valjanosti (nije istekao niti još ne vrijedi). */
    val jeValjan: Boolean get() = runCatching { certificate.checkValidity(); true }.getOrDefault(false)

    val vrijediDo: java.util.Date get() = certificate.notAfter

    companion object {
        /**
         * Učita PKCS#12 spremnik i izvuče prvi unos koji sadrži privatni ključ.
         *
         * @throws IllegalArgumentException ako je lozinka pogrešna ili nema privatnog ključa.
         */
        fun load(input: InputStream, password: CharArray): FiskalCertificate {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(input, password)

            val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
                ?: throw IllegalArgumentException("Certifikat ne sadrži privatni ključ.")

            val key = ks.getKey(alias, password) as? PrivateKey
                ?: throw IllegalArgumentException("Unos '$alias' nema RSA privatni ključ.")
            val cert = ks.getCertificate(alias) as? X509Certificate
                ?: throw IllegalArgumentException("Unos '$alias' nema X.509 certifikat.")

            // Cijeli lanac iz .p12 (leaf prvi); ako ga nema, koristi samo leaf.
            val chain = ks.getCertificateChain(alias)
                ?.mapNotNull { it as? X509Certificate }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(cert)

            return FiskalCertificate(key, chain.first(), chain)
        }
    }
}
