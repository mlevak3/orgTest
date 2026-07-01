package hr.obrt.fiskal.fiskal

import hr.obrt.fiskal.model.Racun

/** Podaci potrebni za prikaz/ispis/email jednog računa. */
data class ReceiptData(
    val naslovTvrtke: String,
    val racun: Racun,
    val jir: String?,
    val zki: String,
    val qrUrl: String,
    val kupac: String = "",
    val kupacOib: String = "",
    val napomena: String = "",
    /** Logo tvrtke (PNG bajtovi), za ispis/PDF/email — opcionalno. */
    val logoPng: ByteArray? = null,
) {
    fun brojRacuna(): String =
        "${racun.brOznRac}/${racun.zaglavlje.oznPosPr}/${racun.zaglavlje.oznNapUr}"
}
