package hr.obrt.fiskal.model

import java.math.BigDecimal
import java.math.RoundingMode

/** Način plaćanja prema tehničkoj specifikaciji (NacinPlacanjaType). */
enum class NacinPlac(val oznaka: String, val opis: String) {
    G("G", "Gotovina"),
    K("K", "Kartica"),
    C("C", "Ček"),
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

/** Jedna stavka računa. Cijena je jedinična cijena s PDV-om kada je obveznik u sustavu PDV-a. */
data class Stavka(
    val naziv: String,
    val kolicina: BigDecimal,
    val jedinicnaCijena: BigDecimal,
    /** PDV stopa u postotcima (npr. 25.00). Ignorira se ako obveznik nije u sustavu PDV-a. */
    val pdvStopa: BigDecimal,
) {
    /** Ukupna vrijednost stavke (količina × jedinična cijena), zaokruženo na 2 decimale. */
    val ukupno: BigDecimal
        get() = kolicina.multiply(jedinicnaCijena).setScale(2, RoundingMode.HALF_UP)
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

/** Kompletan račun spreman za fiskalizaciju. */
data class Racun(
    val zaglavlje: Zaglavlje,
    val brOznRac: Long,
    val datVrijeme: java.util.Date,
    val stavke: List<Stavka>,
    val nacinPlac: NacinPlac,
    val nakDost: Boolean = false,
) {
    /** Porezna grupa: stopa → (osnovica, iznos poreza). */
    data class PdvGrupa(val stopa: BigDecimal, val osnovica: BigDecimal, val iznos: BigDecimal)

    /** Računa PDV grupe iz stavki (samo kada je obveznik u sustavu PDV-a). */
    fun pdvGrupe(): List<PdvGrupa> {
        if (!zaglavlje.uSustavuPdv) return emptyList()
        return stavke
            .groupBy { it.pdvStopa }
            .toSortedMap()
            .map { (stopa, stavke) ->
                val brutoStope = stavke.sumOf2 { it.ukupno }
                // jedinična cijena uključuje PDV → osnovica = bruto / (1 + stopa/100)
                val faktor = BigDecimal.ONE.add(stopa.divide(BigDecimal(100)))
                val osnovica = brutoStope.divide(faktor, 2, RoundingMode.HALF_UP)
                val iznos = brutoStope.subtract(osnovica).setScale(2, RoundingMode.HALF_UP)
                PdvGrupa(stopa.setScale(2, RoundingMode.HALF_UP), osnovica, iznos)
            }
    }

    /** Ukupan iznos računa (zbroj svih stavki), zaokruženo na 2 decimale. */
    val iznosUkupno: BigDecimal
        get() = stavke.sumOf2 { it.ukupno }.setScale(2, RoundingMode.HALF_UP)
}

private inline fun <T> List<T>.sumOf2(selector: (T) -> BigDecimal): BigDecimal =
    fold(BigDecimal.ZERO) { acc, e -> acc.add(selector(e)) }
