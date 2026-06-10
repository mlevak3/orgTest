package hr.obrt.fiskal.fiskal

import java.math.BigDecimal
import java.util.Date

/**
 * Gradi sadržaj QR koda koji se otiskuje na računu, prema tehničkoj specifikaciji.
 *
 * QR sadrži poveznicu na servis Porezne uprave za provjeru računa s parametrima:
 *  - `jir` (36 znakova) ako je račun fiskaliziran, inače `zki` (32 znaka) — npr. kod
 *     naknadne dostave kada JIR još nije dodijeljen,
 *  - `datv` — datum i vrijeme izdavanja u formatu GGGGMMDD_HHMM,
 *  - `izn` — ukupan iznos računa izražen u centima (lipama) kao cijeli broj.
 *
 * Primjer:
 * `https://porezna.gov.hr/rn?jir=...&datv=20260610_2034&izn=12500`
 */
object QrCodeContent {

    const val BASE_URL = "https://porezna.gov.hr/rn"

    fun build(jir: String?, zki: String, datVrijeme: Date, iznosUkupno: BigDecimal): String {
        val idParam = if (!jir.isNullOrBlank()) "jir=$jir" else "zki=$zki"
        val datv = FiskalFormat.qrDateTime(datVrijeme)
        val izn = FiskalFormat.amountInCents(iznosUkupno)
        return "$BASE_URL?$idParam&datv=$datv&izn=$izn"
    }
}
