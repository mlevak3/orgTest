package hr.obrt.fiskal.fiskal

import hr.obrt.fiskal.model.Racun
import java.util.UUID

/**
 * Gradi `RacunZahtjev` XML element u kanonskom obliku (Exclusive C14N).
 *
 * Element se serijalizira deterministički — bez XML deklaracije, bez praznina
 * između elemenata, atributi i namespace u propisanom redoslijedu — tako da
 * njegova serijalizacija već JEST izlaz Exclusive C14N kanonizacije. Time se
 * izbjegava potreba za zasebnim kanonizatorom prilikom računanja sažetka.
 *
 * Redoslijed elemenata unutar `<Racun>` slijedi XSD shemu (RacunType).
 */
object RacunXmlBuilder {

    const val F73_NS = "http://www.apis-it.hr/fin/2012/types/f73"
    const val SIGN_REFERENCE_ID = "signXmlId"

    data class Built(
        /** Kanonski string elementa `<RacunZahtjev …>…</RacunZahtjev>` (bez potpisa). */
        val racunZahtjev: String,
        val idPoruke: String,
        val zki: String,
    )

    fun build(racun: Racun, zki: String, idPoruke: String = UUID.randomUUID().toString()): Built {
        val z = racun.zaglavlje
        val datVrijeme = FiskalFormat.dateTime(racun.datVrijeme)

        val sb = StringBuilder(1024)
        sb.append("<RacunZahtjev xmlns=\"").append(F73_NS)
            .append("\" Id=\"").append(SIGN_REFERENCE_ID).append("\">")

        // Zaglavlje
        sb.append("<Zaglavlje>")
        sb.el("IdPoruke", idPoruke)
        sb.el("DatumVrijeme", datVrijeme)
        sb.append("</Zaglavlje>")

        // Racun
        sb.append("<Racun>")
        sb.el("Oib", z.oib)
        sb.el("USustPdv", if (z.uSustavuPdv) "true" else "false")
        sb.el("DatVrijeme", datVrijeme)
        sb.el("OznSlijed", z.oznSlijed.oznaka)

        sb.append("<BrRac>")
        sb.el("BrOznRac", racun.brOznRac.toString())
        sb.el("OznPosPr", z.oznPosPr)
        sb.el("OznNapUr", z.oznNapUr)
        sb.append("</BrRac>")

        val pdvGrupe = racun.pdvGrupe()
        if (pdvGrupe.isNotEmpty()) {
            sb.append("<Pdv>")
            for (g in pdvGrupe) {
                sb.append("<Porez>")
                sb.el("Stopa", FiskalFormat.amount(g.stopa))
                sb.el("Osnovica", FiskalFormat.amount(g.osnovica))
                sb.el("Iznos", FiskalFormat.amount(g.iznos))
                sb.append("</Porez>")
            }
            sb.append("</Pdv>")
        }

        sb.el("IznosUkupno", FiskalFormat.amount(racun.iznosUkupno))
        sb.el("NacinPlac", racun.nacinPlac.oznaka)
        sb.el("OibOper", z.oibOper)
        sb.el("ZastKod", zki)
        sb.el("NakDost", if (racun.nakDost) "true" else "false")
        // OibPrimateljaRacuna je zadnji element u RacunType sekvenci (nakon NakDost i
        // opcionalnih ParagonBrRac/SpecNamj koje ne šaljemo). Emitira se samo kad
        // postoji OIB kupca — inače element izostaje (minOccurs=0), pa je poruka za
        // obični B2C račun bajt-identična kao i prije.
        racun.oibPrimatelja?.takeIf { it.isNotBlank() }?.let { sb.el("OibPrimateljaRacuna", it) }
        sb.append("</Racun>")

        sb.append("</RacunZahtjev>")

        return Built(sb.toString(), idPoruke, zki)
    }

    private fun StringBuilder.el(name: String, value: String) {
        append('<').append(name).append('>')
        appendEscaped(value)
        append("</").append(name).append('>')
    }

    private fun StringBuilder.appendEscaped(text: String) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
        }
    }
}
