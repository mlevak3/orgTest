package hr.obrt.fiskal

import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.RacunXmlBuilder
import hr.obrt.fiskal.fiskal.XmlSigner
import hr.obrt.fiskal.fiskal.ZkiGenerator
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import hr.obrt.fiskal.model.Zaglavlje
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Calendar
import java.util.Date
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.parsers.DocumentBuilderFactory

class FiskalCoreTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()
    private val cert: X509Certificate = selfSigned(keyPair)

    private val racun = Racun(
        zaglavlje = Zaglavlje(
            oib = "12345678901",
            uSustavuPdv = true,
            oznPosPr = "POSL1",
            oznNapUr = "1",
            oznSlijed = OznSlijed.P,
            oibOper = "98765432109",
        ),
        brOznRac = 1,
        datVrijeme = Date(0),
        stavke = listOf(
            Stavka(
                naziv = "Usluga",
                kolicina = BigDecimal("1"),
                pdvStopa = BigDecimal("25"),
                neto = BigDecimal("100.00"),
                pdvIznos = BigDecimal("25.00"),
                ukupno = BigDecimal("125.00"),
            )
        ),
        nacinPlac = NacinPlac.G,
    )

    @Test
    fun zki_je_32_heksadecimalna_znaka_i_deterministican() {
        val zki = generirajZki()
        assertTrue("ZKI mora biti 32 hex znaka: $zki", Regex("^[0-9a-f]{32}$").matches(zki))
        assertEquals("ZKI mora biti determinističan", zki, generirajZki())
    }

    @Test
    fun zki_odgovara_nezavisnom_izracunu() {
        val data = "12345678901" + FiskalFormat.dateTime(Date(0)) +
            "1" + "POSL1" + "1" + "125.00"
        val sig = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private); update(data.toByteArray(Charsets.UTF_8))
        }.sign()
        val md5 = MessageDigest.getInstance("MD5").digest(sig)
        val ocekivano = md5.joinToString("") { "%02x".format(it) }
        assertEquals(ocekivano, generirajZki())
    }

    @Test
    fun racun_zahtjev_je_ispravan_xml_i_sadrzi_kljucna_polja() {
        val built = RacunXmlBuilder.build(racun, generirajZki())
        val doc = parse("<root>${built.racunZahtjev}</root>") // omotač samo radi parsiranja
        assertTrue(built.racunZahtjev.contains("<Oib>12345678901</Oib>"))
        assertTrue(built.racunZahtjev.contains("<IznosUkupno>125.00</IznosUkupno>"))
        assertTrue(built.racunZahtjev.contains("<ZastKod>"))
        assertEquals("RacunZahtjev", doc.documentElement.firstChild.localName ?: doc.documentElement.firstChild.nodeName)
    }

    @Test
    fun potpis_se_validira_standardnim_xmldsig_validatorom() {
        val built = RacunXmlBuilder.build(racun, generirajZki())
        val signed = XmlSigner.sign(built.racunZahtjev, keyPair.private, cert)

        val doc = parse(signed)
        val root = doc.documentElement as Element
        // Reference URI="#signXmlId" → atribut Id mora biti tipa ID.
        root.setIdAttribute("Id", true)

        val sigNode = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0)
        val ctx = DOMValidateContext(KeySelector.singletonKeySelector(keyPair.public), sigNode)
        // CIS zahtijeva rsa-sha1/sha1; JDK ih po defaultu blokira "secure validationom".
        ctx.setProperty("org.apache.jcp.xml.dsig.secureValidation", java.lang.Boolean.FALSE)
        ctx.setProperty("org.jcp.xml.dsig.secureValidation", java.lang.Boolean.FALSE)
        val signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(ctx)

        assertTrue("Potpis nije valjan po XML-DSig (exc-c14n/sha1/rsa-sha1)", signature.validate(ctx))
    }

    @Test
    fun digest_reference_odgovara_sha1_kanonskog_racuna() {
        val built = RacunXmlBuilder.build(racun, generirajZki())
        val signed = XmlSigner.sign(built.racunZahtjev, keyPair.private, cert)
        val digestValue = Regex("<DigestValue>([^<]+)</DigestValue>").find(signed)!!.groupValues[1]
        val ocekivano = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest(built.racunZahtjev.toByteArray(Charsets.UTF_8))
        )
        assertEquals(ocekivano, digestValue)
    }

    private fun generirajZki(): String = ZkiGenerator.generate(
        privateKey = keyPair.private,
        oib = racun.zaglavlje.oib,
        datVrijeme = FiskalFormat.dateTime(racun.datVrijeme),
        brOznRac = racun.brOznRac.toString(),
        oznPosPr = racun.zaglavlje.oznPosPr,
        oznNapUr = racun.zaglavlje.oznNapUr,
        iznosUkupno = FiskalFormat.amount(racun.iznosUkupno),
    )

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    companion object {
        init { Security.addProvider(BouncyCastleProvider()) }

        private fun selfSigned(kp: KeyPair): X509Certificate {
            val now = Date()
            val end = Calendar.getInstance().apply { time = now; add(Calendar.YEAR, 1) }.time
            val name = org.bouncycastle.asn1.x500.X500Name("CN=Test Obrt, O=Test, C=HR, serialNumber=12345678901")
            val serial = java.math.BigInteger.valueOf(System.currentTimeMillis())
            val builder = JcaX509v3CertificateBuilder(name, serial, now, end, name, kp.public)
            val signer = JcaContentSignerBuilder("SHA256WithRSA").build(kp.private)
            return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
        }
    }

    /** Pomoćni KeySelector koji uvijek vraća zadani ključ. */
    @Suppress("unused")
    private class FixedKeySelector(private val key: PublicKey) : KeySelector() {
        override fun select(ki: javax.xml.crypto.dsig.keyinfo.KeyInfo?, p: Purpose?, m: AlgorithmMethod?, c: XMLCryptoContext?): KeySelectorResult =
            KeySelectorResult { key }
    }
}
