package hr.obrt.fiskal.fiskal

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Gradi ESC/POS naredbe za ispis računa na termalni POS pisač (npr. Bixolon
 * SPP-R200II, 58 mm / 32 stupaca). Kompatibilno sa standardnim ESC/POS skupom
 * (Epson-kompatibilni pisači).
 *
 * QR kôd ispisuje sam pisač (naredbe GS ( k) iz teksta poveznice — ne treba
 * generirati sliku.
 *
 * Pisač obično koristi jednobajtnu kodnu stranicu bez hrvatskih dijakritika,
 * pa se tekst transliterira (č/ć→c, š→s, ž→z, đ→dj) radi pouzdanog ispisa na
 * svim modelima/firmverima bez ovisnosti o točnoj kodnoj stranici.
 */
object EscPosReceiptBuilder {

    private const val WIDTH = 32
    private const val ESC = 0x1B
    private const val GS = 0x1D

    fun build(data: ReceiptData): ByteArray {
        val out = ByteArrayOutputStream()
        val r = data.racun
        val z = r.zaglavlje

        fun raw(b: IntArray) { for (x in b) out.write(x) }
        fun bytes(b: ByteArray) { out.write(b) }
        fun text(s: String) = bytes(translit(s).toByteArray(Charsets.US_ASCII))
        fun ln(s: String = "") { text(s); text("\n") }
        fun align(a: Int) = raw(intArrayOf(ESC, 0x61, a)) // 0=left 1=center 2=right
        fun bold(on: Boolean) = raw(intArrayOf(ESC, 0x45, if (on) 1 else 0))
        fun bigOn() = raw(intArrayOf(GS, 0x21, 0x11)) // dvostruka širina+visina
        fun bigOff() = raw(intArrayOf(GS, 0x21, 0x00))

        raw(intArrayOf(ESC, 0x40)) // init

        align(1); bold(true); ln(data.naslovTvrtke); bold(false)
        ln("OIB: ${z.oib}")
        ln("Racun: ${data.brojRacuna()}")
        ln(SimpleDateFormat("dd.MM.yyyy. HH:mm:ss", Locale.ROOT).format(r.datVrijeme))
        align(0)
        ln("Djelatnik: ${z.oibOper}")
        if (data.kupac.isNotBlank() || data.kupacOib.isNotBlank()) {
            ln("Kupac: ${data.kupac}" + if (data.kupacOib.isNotBlank()) " (OIB ${data.kupacOib})" else "")
            if (data.kupacAdresa.isNotBlank()) ln(data.kupacAdresa)
        }
        ln("=".repeat(WIDTH))

        bold(true); ln("NAZIV ARTIKLA"); bold(false)
        ln(redak("Kolicina  x  Cijena", "Iznos", WIDTH))
        ln("-".repeat(WIDTH))
        r.stavke.forEach { s ->
            ln(s.naziv)
            val kolJed = "${s.kolicina.toPlainString()} ${s.jedMjere}"
            val cijena = FiskalFormat.amount(s.jedinicnaCijena())
            ln(redak("$kolJed x $cijena", FiskalFormat.amount(s.ukupno), WIDTH))
        }
        ln("=".repeat(WIDTH))

        bigOn()
        ln(redak("TOTAL:", "${FiskalFormat.amount(r.iznosUkupno)} EUR", WIDTH / 2))
        bigOff()
        ln("-".repeat(WIDTH))

        bold(true); ln("NACIN PLACANJA"); bold(false)
        ln(redak(r.nacinPlac.opis, "${FiskalFormat.amount(r.iznosUkupno)} EUR", WIDTH))
        if (data.napomena.isNotBlank()) { ln("-".repeat(WIDTH)); ln("Napomena: ${data.napomena}") }

        if (z.uSustavuPdv) {
            ln("-".repeat(WIDTH))
            bold(true); ln("REKAPITULACIJA POREZA"); bold(false)
            ln(stupci("%PDV", "Osnovica", "PDV", "Ukupno"))
            var sumOsn = java.math.BigDecimal.ZERO
            var sumPdv = java.math.BigDecimal.ZERO
            r.pdvGrupe().forEach { g ->
                ln(stupci(FiskalFormat.amount(g.stopa), FiskalFormat.amount(g.osnovica), FiskalFormat.amount(g.iznos), FiskalFormat.amount(g.osnovica.add(g.iznos))))
                sumOsn = sumOsn.add(g.osnovica); sumPdv = sumPdv.add(g.iznos)
            }
            ln(stupci("", "TOTAL:", "", ""))
            ln(stupci("", FiskalFormat.amount(sumOsn), FiskalFormat.amount(sumPdv), FiskalFormat.amount(sumOsn.add(sumPdv))))
        } else {
            ln("-".repeat(WIDTH))
            ln("Nije u sustavu PDV-a.")
        }
        ln("=".repeat(WIDTH))

        ln("ZKI: ${data.zki}")
        ln("JIR: ${data.jir ?: "(nije dodijeljen - naknadna dostava)"}")
        ln()

        align(1)
        qrCode(::bytes, ::raw, data.qrUrl)
        text("\n")
        ln("Provjera: porezna.gov.hr/rn")
        ln()
        bold(true); ln("Hvala na posjeti!"); bold(false)
        align(0)

        text("\n\n\n")
        raw(intArrayOf(GS, 0x56, 66, 0)) // djelomično odrezivanje papira

        return out.toByteArray()
    }

    /** Četiri stupca jednakih širina unutar [WIDTH] (za tablicu rekapitulacije poreza). */
    private fun stupci(a: String, b: String, c: String, d: String): String {
        val w = WIDTH / 4
        fun cell(s: String) = if (s.length >= w) s.take(w) else s.padEnd(w)
        return cell(a) + cell(b) + cell(c) + cell(d)
    }

    /** Kratka testna stranica — provjera veze i ispravnosti pisača. */
    fun buildTestPage(nazivTvrtke: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun raw(b: IntArray) { for (x in b) out.write(x) }
        fun text(s: String) = out.write(translit(s).toByteArray(Charsets.US_ASCII))
        fun ln(s: String = "") { text(s); text("\n") }

        raw(intArrayOf(ESC, 0x40))
        raw(intArrayOf(ESC, 0x61, 1))
        ln("=== TESTNI ISPIS ===")
        ln(nazivTvrtke)
        ln(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(java.util.Date()))
        ln()
        ln("Ako čitaš ovo, pisač je ispravno")
        ln("povezan i spreman za ispis racuna.")
        text("\n\n\n")
        raw(intArrayOf(GS, 0x56, 66, 0))
        return out.toByteArray()
    }

    /** Lijevi i desni tekst u retku širine [width] (padding razmacima). */
    private fun redak(lijevo: String, desno: String, width: Int): String {
        val slobodno = width - lijevo.length - desno.length
        return if (slobodno > 0) lijevo + " ".repeat(slobodno) + desno else "$lijevo $desno"
    }

    /** ESC/POS GS ( k naredbe za QR kôd (model 2, srednja veličina, ispravak L). */
    private fun qrCode(bytesOut: (ByteArray) -> Unit, raw: (IntArray) -> Unit, data: String) {
        val payload = data.toByteArray(Charsets.UTF_8)
        val len = payload.size + 3
        val pL = len and 0xFF
        val pH = (len shr 8) and 0xFF

        raw(intArrayOf(GS, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00)) // model 2
        raw(intArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05))       // veličina modula
        raw(intArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))       // ispravak grešaka L
        raw(intArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))          // pohrani podatke (zaglavlje)
        bytesOut(payload)
        raw(intArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))       // ispiši
    }

    private fun translit(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(
            when (c) {
                'č', 'ć' -> 'c'
                'Č', 'Ć' -> 'C'
                'š' -> 's'; 'Š' -> 'S'
                'ž' -> 'z'; 'Ž' -> 'Z'
                'đ' -> 'd'; 'Đ' -> 'D'
                else -> if (c.code in 32..126 || c == '\n') c else '?'
            }
        )
        return sb.toString()
    }
}
