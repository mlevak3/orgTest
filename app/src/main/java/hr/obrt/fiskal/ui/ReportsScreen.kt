package hr.obrt.fiskal.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.fiskal.FiskalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var period by remember { mutableStateOf(PeriodIzvjestaja.DANAS) }
    var prilagodjenoOd by remember { mutableStateOf(pocetakDanasnjegDana()) }
    var prilagodjenoDo by remember { mutableStateOf(System.currentTimeMillis()) }
    val izvjestaj = remember(period, vm.selected.value, prilagodjenoOd, prilagodjenoDo) {
        vm.izvjestaj(period, prilagodjenoOd, prilagodjenoDo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Izvještaji") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Natrag") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PeriodIzvjestaja.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.naziv) })
                    }
                }
            }

            if (period == PeriodIzvjestaja.PRILAGODJENO) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { odaberiDatumVrijeme(ctx, prilagodjenoOd) { prilagodjenoOd = it } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Od: ${formatDatumVrijeme(prilagodjenoOd)}") }
                    OutlinedButton(
                        onClick = { odaberiDatumVrijeme(ctx, prilagodjenoDo) { prilagodjenoDo = it } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Do: ${formatDatumVrijeme(prilagodjenoDo)}") }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Broj računa", style = MaterialTheme.typography.labelMedium)
                            Text("${izvjestaj.brojRacuna}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ukupan promet", style = MaterialTheme.typography.labelMedium)
                            Text("${izvjestaj.ukupanPromet.toPlainString()} €", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item { NaslovSekcije("Po načinima plaćanja") }
            if (izvjestaj.poNacinuPlac.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.poNacinuPlac) { s ->
                IzvjestajRedak(s.nacin.opis, "${s.brojRacuna} rač. · ${s.ukupno.toPlainString()} €")
            }

            item { NaslovSekcije("Rekapitulacija PDV-a") }
            if (izvjestaj.pdvRekapitulacija.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.pdvRekapitulacija) { g ->
                IzvjestajRedak(
                    "PDV ${g.stopa}%",
                    "osnovica ${FiskalFormat.amount(g.osnovica)} · PDV ${FiskalFormat.amount(g.pdv)} · ukupno ${FiskalFormat.amount(g.ukupno)} €",
                )
            }

            item { NaslovSekcije("Prodaja po artiklima") }
            if (izvjestaj.poArtiklima.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.poArtiklima) { a ->
                IzvjestajRedak(a.naziv, "kol. ${a.kolicina.toPlainString()} · ${a.ukupno.toPlainString()} €")
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

private fun pocetakDanasnjegDana(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatDatumVrijeme(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(millis))

/** Odabir datuma pa vremena (do razine minute) preko standardnih Android dijaloga. */
private fun odaberiDatumVrijeme(ctx: Context, pocetno: Long, onOdabrano: (Long) -> Unit) {
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

@Composable
private fun NaslovSekcije(naslov: String) {
    Text(naslov, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PrazniRedak() {
    Text("Nema podataka za odabrani period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IzvjestajRedak(naziv: String, vrijednost: String) {
    ElevatedCard {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(naziv, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(vrijednost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
