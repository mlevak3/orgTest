package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.fiskal.FiskalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var period by remember { mutableStateOf(PeriodIzvjestaja.DANAS) }
    val izvjestaj = remember(period, vm.selected.value) { vm.izvjestaj(period) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PeriodIzvjestaja.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.naziv) })
                    }
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
