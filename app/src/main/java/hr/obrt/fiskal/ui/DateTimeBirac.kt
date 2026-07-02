package hr.obrt.fiskal.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun formatDatumVrijeme(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(millis))

/** Odabir datuma pa vremena (do razine minute) preko standardnih Android dijaloga. */
internal fun odaberiDatumVrijeme(ctx: Context, pocetno: Long, onOdabrano: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = pocetno }
    DatePickerDialog(
        ctx,
        { _, godina, mjesec, dan ->
            TimePickerDialog(
                ctx,
                { _, sat, minuta ->
                    val odabrano = Calendar.getInstance().apply {
                        set(godina, mjesec, dan, sat, minuta, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onOdabrano(odabrano.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true,
            ).show()
        },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}
