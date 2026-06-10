package hr.obrt.fiskal.fiskal

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Dodaje obvezni XML digitalni potpis (XML-DSig, enveloped) na `RacunZahtjev`
 * prema tehničkoj specifikaciji:
 *
 *  - CanonicalizationMethod / Transform: Exclusive C14N (xml-exc-c14n#)
 *  - SignatureMethod: rsa-sha1
 *  - DigestMethod: sha1
 *  - KeyInfo: X509Certificate + X509IssuerSerial
 *
 * Sažetak `RacunZahtjev`-a računa se nad njegovom kanonskom serijalizacijom
 * (RacunXmlBuilder već proizvodi C14N oblik), a `<Signature>` se umeće kao
 * posljednje dijete elementa `RacunZahtjev` (enveloped potpis).
 */
object XmlSigner {

    private const val DSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val C14N_EXCL = "http://www.w3.org/2001/10/xml-exc-c14n#"
    private const val ENVELOPED = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    private const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
    private const val SHA1 = "http://www.w3.org/2000/09/xmldsig#sha1"

    /**
     * @param racunZahtjev kanonski string `<RacunZahtjev …>…</RacunZahtjev>` bez potpisa
     * @return isti element s umetnutim `<Signature>` prije zatvarajućeg taga
     */
    fun sign(
        racunZahtjev: String,
        privateKey: PrivateKey,
        certificate: X509Certificate,
        referenceId: String = RacunXmlBuilder.SIGN_REFERENCE_ID,
    ): String {
        val b64 = Base64.getEncoder()

        // 1) DigestValue nad kanonskim RacunZahtjev-om.
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(racunZahtjev.toByteArray(Charsets.UTF_8))
        val digestValue = b64.encodeToString(digest)

        // 2) SignedInfo (unutrašnji sadržaj jednom; namespace samo za potpisivanje).
        val signedInfoInner =
            "<CanonicalizationMethod Algorithm=\"$C14N_EXCL\"></CanonicalizationMethod>" +
            "<SignatureMethod Algorithm=\"$RSA_SHA1\"></SignatureMethod>" +
            "<Reference URI=\"#$referenceId\">" +
            "<Transforms>" +
            "<Transform Algorithm=\"$ENVELOPED\"></Transform>" +
            "<Transform Algorithm=\"$C14N_EXCL\"></Transform>" +
            "</Transforms>" +
            "<DigestMethod Algorithm=\"$SHA1\"></DigestMethod>" +
            "<DigestValue>$digestValue</DigestValue>" +
            "</Reference>"

        // Verifikator kanonizira SignedInfo samostalno → namespace se mora renderirati.
        val signedInfoForSigning =
            "<SignedInfo xmlns=\"$DSIG_NS\">$signedInfoInner</SignedInfo>"

        // 3) SignatureValue = RSA-SHA1 nad kanonskim SignedInfo-om.
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(privateKey)
        signer.update(signedInfoForSigning.toByteArray(Charsets.UTF_8))
        val signatureValue = b64.encodeToString(signer.sign())

        // 4) KeyInfo
        val certB64 = b64.encodeToString(certificate.encoded)
        val issuerName = certificate.issuerX500Principal.name // RFC 2253
        val serial = certificate.serialNumber.toString()

        // 5) Potpuni <Signature> (SignedInfo nasljeđuje namespace s elementa Signature).
        val signature =
            "<Signature xmlns=\"$DSIG_NS\">" +
            "<SignedInfo>$signedInfoInner</SignedInfo>" +
            "<SignatureValue>$signatureValue</SignatureValue>" +
            "<KeyInfo>" +
            "<X509Data>" +
            "<X509Certificate>$certB64</X509Certificate>" +
            "<X509IssuerSerial>" +
            "<X509IssuerName>${escape(issuerName)}</X509IssuerName>" +
            "<X509SerialNumber>$serial</X509SerialNumber>" +
            "</X509IssuerSerial>" +
            "</X509Data>" +
            "</KeyInfo>" +
            "</Signature>"

        val closing = "</RacunZahtjev>"
        require(racunZahtjev.endsWith(closing)) { "Neočekivan oblik RacunZahtjev-a." }
        return racunZahtjev.dropLast(closing.length) + signature + closing
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(c)
        }
    }
}
