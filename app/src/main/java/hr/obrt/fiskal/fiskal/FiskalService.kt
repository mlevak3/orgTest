package hr.obrt.fiskal.fiskal

import hr.obrt.fiskal.model.Racun

/** Konačni ishod fiskalizacije: uvijek vraća ZKI (računa se lokalno), a JIR ako je uspjelo. */
data class FiskalIshod(
    val racun: Racun,
    val zki: String,
    val jir: String?,
    val rezultat: FiskalRezultat,
    /** Sadržaj QR koda za provjeru računa (koristi JIR, a ZKI ako JIR nedostaje). */
    val qrUrl: String,
    /** Potpisani SOAP zahtjev (za dijagnostiku / ispis / log fiskalizacije). */
    val zahtjevXml: String,
    /** HTTP status odgovora CIS-a (-1 ako zahtjev nije ni poslan — mrežna greška). */
    val httpKod: Int = -1,
    /** Sirovo tijelo SOAP odgovora CIS-a (za log fiskalizacije). */
    val odgovorXml: String = "",
)

/**
 * Orkestrira cijeli postupak fiskalizacije jednog računa:
 * ZKI → RacunZahtjev → XML potpis → SOAP → JIR.
 *
 * ZKI se izračunava lokalno i vrijedi neovisno o uspjehu slanja (mora se
 * otisnuti na računu čak i u slučaju nedostupnosti CIS-a / naknadne dostave).
 */
class FiskalService(
    private val certificate: FiskalCertificate,
    private val okolina: FiskalOkolina,
    private val ignoreTlsTrust: Boolean = false,
    private val extraCaCerts: List<java.security.cert.X509Certificate> = emptyList(),
) {
    fun fiskaliziraj(racun: Racun): FiskalIshod {
        val z = racun.zaglavlje
        val datVrijeme = FiskalFormat.dateTime(racun.datVrijeme)

        val zki = ZkiGenerator.generate(
            privateKey = certificate.privateKey,
            oib = z.oib,
            datVrijeme = datVrijeme,
            brOznRac = racun.brOznRac.toString(),
            oznPosPr = z.oznPosPr,
            oznNapUr = z.oznNapUr,
            iznosUkupno = FiskalFormat.amount(racun.iznosUkupno),
        )

        val built = RacunXmlBuilder.build(racun, zki)
        val signed = XmlSigner.sign(
            built.racunZahtjev, certificate.privateKey, certificate.certificate,
            chain = certificate.chain,
        )
        val soap = wrapSoap(signed)

        val httpOdgovor = FiskalClient(okolina, ignoreTlsTrust, extraCaCerts).posalji(soap)
        val rezultat = httpOdgovor.rezultat
        val jir = (rezultat as? FiskalRezultat.Uspjeh)?.jir

        val qrUrl = QrCodeContent.build(jir, zki, racun.datVrijeme, racun.iznosUkupno)

        return FiskalIshod(
            racun = racun,
            zki = zki,
            jir = jir,
            rezultat = rezultat,
            qrUrl = qrUrl,
            zahtjevXml = soap,
            httpKod = httpOdgovor.httpKod,
            odgovorXml = httpOdgovor.rawTijelo,
        )
    }

    private fun wrapSoap(racunZahtjevSigned: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
        "<soapenv:Body>" +
        racunZahtjevSigned +
        "</soapenv:Body>" +
        "</soapenv:Envelope>"
}
