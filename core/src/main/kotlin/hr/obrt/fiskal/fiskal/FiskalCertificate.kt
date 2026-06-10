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
) {
    /** OIB iz certifikata (zadnjih 11 znamenki iz subject-a), ako se može pročitati. */
    val oibIzCertifikata: String? by lazy {
        // FINA aplikacijski certifikati u CN/serialNumber sadrže OIB obveznika.
        Regex("\\d{11}").find(certificate.subjectX500Principal.name)?.value
    }

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

            return FiskalCertificate(key, cert)
        }
    }
}
