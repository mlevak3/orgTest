package hr.obrt.fiskal.fiskal

import hr.obrt.fiskal.model.Racun

/** Konačni ishod fiskalizacije: uvijek vraća ZKI (računa se lokalno), a JIR ako je uspjelo. */
data class FiskalIshod(
    val zki: String,
    val jir: String?,
    val rezultat: FiskalRezultat,
    /** Potpisani SOAP zahtjev (za dijagnostiku / ispis). */
    val zahtjevXml: String,
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
        val signed = XmlSigner.sign(built.racunZahtjev, certificate.privateKey, certificate.certificate)
        val soap = wrapSoap(signed)

        val rezultat = FiskalClient(okolina, ignoreTlsTrust).posalji(soap)
        val jir = (rezultat as? FiskalRezultat.Uspjeh)?.jir

        return FiskalIshod(zki = zki, jir = jir, rezultat = rezultat, zahtjevXml = soap)
    }

    private fun wrapSoap(racunZahtjevSigned: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
        "<soapenv:Body>" +
        racunZahtjevSigned +
        "</soapenv:Body>" +
        "</soapenv:Envelope>"
}
