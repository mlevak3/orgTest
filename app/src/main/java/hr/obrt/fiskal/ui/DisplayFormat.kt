package hr.obrt.fiskal.ui

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Prikaz iznosa u hrvatskom formatu (decimalni zarez, tisućica točka) — samo za UI
 * (BRAND-UPUTE.md pogl. 5). Ovo je isključivo kozmetički prikaz; fiskalizacija i format
 * računa i dalje koriste [hr.obrt.fiskal.fiskal.FiskalFormat] (točka, bez tisućica).
 */
private val hrSymbols = DecimalFormatSymbols(Locale.ROOT).apply {
    decimalSeparator = ','
    groupingSeparator = '.'
}
private val hrDecimalFormat = DecimalFormat("#,##0.00", hrSymbols)

fun hrEur(value: BigDecimal): String = "${hrDecimalFormat.format(value)} €"

/** Količina bez suvišnih decimala (29, ne 29.00; 1,5 s decimalnim zarezom). */
fun hrKolicina(value: BigDecimal): String {
    val bezNula = value.stripTrailingZeros()
    val normalizirano = if (bezNula.scale() < 0) bezNula.setScale(0) else bezNula
    return normalizirano.toPlainString().replace('.', ',')
}
