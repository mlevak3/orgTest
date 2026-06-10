package hr.obrt.fiskal.fiskal

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formati propisani tehničkom specifikacijom fiskalizacije v2.6.
 *
 * - Datum/vrijeme: dd.MM.yyyy'T'HH:mm:ss (npr. 09.06.2026T20:00:00)
 * - Iznosi: točka kao decimalni razdjelnik, točno 2 decimale, bez razdjelnika tisuća.
 */
object FiskalFormat {

    /** Format datuma i vremena za polja DatVrijeme / DatumVrijeme te za ulaz ZKI-ja. */
    private const val DATETIME_PATTERN = "dd.MM.yyyy'T'HH:mm:ss"

    fun dateTime(date: Date): String =
        SimpleDateFormat(DATETIME_PATTERN, Locale.ROOT).format(date)

    /** Iznos u obliku "0.00" (Locale.ROOT → uvijek točka). */
    fun amount(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
