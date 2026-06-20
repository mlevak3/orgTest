package hr.obrt.fiskal.fiskal

import hr.obrt.fiskal.model.Racun

/** Podaci potrebni za prikaz/ispis/email jednog računa. */
data class ReceiptData(
    val naslovTvrtke: String,
    val racun: Racun,
    val jir: String?,
    val zki: String,
    val qrUrl: String,
) {
    fun brojRacuna(): String =
        "${racun.brOznRac}/${racun.zaglavlje.oznPosPr}/${racun.zaglavlje.oznNapUr}"

    companion object {
        fun fromIshod(naslovTvrtke: String, ishod: FiskalIshod) = ReceiptData(
            naslovTvrtke = naslovTvrtke,
            racun = ishod.racun,
            jir = ishod.jir,
            zki = ishod.zki,
            qrUrl = ishod.qrUrl,
        )
    }
}
