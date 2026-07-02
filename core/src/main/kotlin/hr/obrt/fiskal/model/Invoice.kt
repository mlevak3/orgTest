package hr.obrt.fiskal.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Način plaćanja prema tehničkoj specifikaciji (NacinPlacanjaType). */
enum class NacinPlac(val oznaka: String, val opis: String) {
    G("G", "Gotovina"),
    K("K", "Kartica"),
    T("T", "Transakcijski račun"),
    O("O", "Ostalo");

    companion object {
        fun fromOznaka(o: String) = entries.firstOrNull { it.oznaka == o } ?: G
    }
}

/** Oznaka slijednosti broja računa (OznSlijed). */
enum class OznSlijed(val oznaka: String, val opis: String) {
    N("N", "Na razini naplatnog uređaja"),
    P("P", "Na razini poslovnog prostora");
}

/**
 * Jedna stavka računa. Iznosi su eksplicitni kako bi korisnik mogao ručno
 * korigirati PDV/ukupno (zaokruživanje):
 *  - [neto]     — osnovica bez PDV-a,
 *  - [pdvIznos] — iznos PDV-a,
 *  - [ukupno]   — neto + PDV (ili ručno postavljeno).
 *
 * Za obveznika koji nije u sustavu PDV-a: pdvStopa = 0, pdvIznos = 0, neto = ukupno.
 */
data class Stavka(
    val naziv: String,
    val kolicina: BigDecimal,
    val pdvStopa: BigDecimal,
    val neto: BigDecimal,
    val pdvIznos: BigDecimal,
    val ukupno: BigDecimal,
    /** Jedinica mjere (npr. kom, kg, h) — samo za prikaz na ispisu, ne šalje se u fiskalizaciju. */
    val jedMjere: String = "kom",
) {
    /** Bruto jedinična cijena (s PDV-om), izvedena iz ukupnog iznosa i količine — za prikaz na ispisu. */
    fun jedinicnaCijena(): BigDecimal =
        if (kolicina.signum() != 0) ukupno.divide(kolicina, 2, java.math.RoundingMode.HALF_UP) else ukupno
}

/** Zaglavlje računa — fiksni podaci obveznika i poslovnog prostora. */
data class Zaglavlje(
    val oib: String,
    val uSustavuPdv: Boolean,
    val oznPosPr: String,
    val oznNapUr: String,
    val oznSlijed: OznSlijed,
    val oibOper: String,
)

/** Validacija hrvatskog OIB-a (11 znamenki + kontrolna znamenka po ISO 7064, MOD 11,10). */
object Oib {
    fun jeValjan(oib: String): Boolean {
        if (oib.length != 11 || !oib.all { it.isDigit() }) return false
        var ostatak = 10
        for (i in 0 until 10) {
            ostatak = (oib[i].digitToInt() + ostatak) % 10
            if (ostatak == 0) ostatak = 10
            ostatak = (ostatak * 2) % 11
        }
        val kontrolna = (11 - ostatak) % 10
        return kontrolna == oib[10].digitToInt()
    }
}

/** Kompletan račun spreman za fiskalizaciju. */
data class Racun(
    val zaglavlje: Zaglavlje,
    val brOznRac: Long,
    val datVrijeme: java.util.Date,
    val stavke: List<Stavka>,
    val nacinPlac: NacinPlac,
    val nakDost: Boolean = false,
    /**
     * OIB primatelja računa (kupca) — šalje se u fiskalizaciju samo za hrvatske
     * kupce s valjanim OIB-om. null/prazno = ne šalje se (obični B2C račun), pa
     * XML ostaje bez tog elementa (minOccurs=0 u shemi). Ne utječe na ZKI.
     */
    val oibPrimatelja: String? = null,
) {
    /** Porezna grupa: stopa → (osnovica, iznos poreza). */
    data class PdvGrupa(val stopa: BigDecimal, val osnovica: BigDecimal, val iznos: BigDecimal)

    /** PDV grupe iz stavki — zbroj neto i PDV iznosa po stopi (samo za PDV obveznika). */
    fun pdvGrupe(): List<PdvGrupa> {
        if (!zaglavlje.uSustavuPdv) return emptyList()
        return stavke
            .groupBy { it.pdvStopa }
            .toSortedMap()
            .map { (stopa, items) ->
                PdvGrupa(
                    stopa = stopa.setScale(2, RoundingMode.HALF_UP),
                    osnovica = items.zbroj { it.neto },
                    iznos = items.zbroj { it.pdvIznos },
                )
            }
    }

    /** Ukupan iznos računa (zbroj svih stavki). */
    val iznosUkupno: BigDecimal
        get() = stavke.zbroj { it.ukupno }
}

private inline fun <T> List<T>.zbroj(selector: (T) -> BigDecimal): BigDecimal =
    fold(BigDecimal.ZERO) { acc, e -> acc.add(selector(e)) }.setScale(2, RoundingMode.HALF_UP)
