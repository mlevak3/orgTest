package hr.obrt.fiskal.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalChip
import hr.obrt.fiskal.ui.components.FiskalEmptyState
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    val ctx = LocalContext.current
    var period by remember { mutableStateOf(PeriodIzvjestaja.DANAS) }
    var prilagodjenoOd by remember { mutableStateOf(pocetakDanasnjegDana()) }
    var prilagodjenoDo by remember { mutableStateOf(System.currentTimeMillis()) }
    val izvjestaj = remember(period, vm.selected.value, prilagodjenoOd, prilagodjenoDo) {
        vm.izvjestaj(period, prilagodjenoOd, prilagodjenoDo)
    }
    val maxPromet = maxOf(
        izvjestaj.poNacinuPlac.maxOfOrNull { it.ukupno.abs() } ?: java.math.BigDecimal.ONE,
        java.math.BigDecimal.ONE,
    )

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Izvještaji", vm.selected.value?.opis())

        LazyColumn(
            Modifier.padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
            contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PeriodIzvjestaja.entries.forEach { p ->
                        FiskalChip(p.naziv, period == p) { period = p }
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
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = t.heroHeader),
                ) {
                    Row(Modifier.padding(FiskalSpacing.card).fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Broj računa", style = MaterialTheme.typography.labelMedium, color = t.oliveInk.copy(alpha = 0.8f))
                            Text("${izvjestaj.brojRacuna}", style = MaterialTheme.typography.headlineSmall, color = t.oliveInk)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ukupan promet", style = MaterialTheme.typography.labelMedium, color = t.oliveInk.copy(alpha = 0.8f))
                            Text(hrEur(izvjestaj.ukupanPromet), style = MaterialTheme.typography.headlineSmall, color = t.oliveInk)
                        }
                    }
                }
            }

            item { NaslovSekcije("Po načinima plaćanja") }
            if (izvjestaj.poNacinuPlac.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.poNacinuPlac) { s ->
                NacinPlacRedak(s.nacin.opis, "${s.brojRacuna} rač. · ${hrEur(s.ukupno)}", s.ukupno.abs(), maxPromet)
            }

            item { NaslovSekcije("Rekapitulacija PDV-a") }
            if (izvjestaj.pdvRekapitulacija.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.pdvRekapitulacija) { g -> PdvRedak(g) }

            item { NaslovSekcije("Prodaja po artiklima") }
            if (izvjestaj.poArtiklima.isEmpty()) item { PrazniRedak() }
            items(izvjestaj.poArtiklima) { a -> ArtiklRedak(a) }
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
    val t = LocalFiskalTokens.current
    Text(naslov, style = MaterialTheme.typography.titleSmall, color = t.ink)
}

@Composable
private fun PrazniRedak() {
    FiskalEmptyState(
        Icons.Rounded.Assessment,
        "Nema podataka",
        "Još nema računa za ovaj period. Novi račun kreiraš gumbom + u donjoj navigaciji.",
    )
}

/** Rekapitulacija po PDV stopi: badge sa stopom, osnovica/ukupno kao meta, iznos PDV-a desno. */
@Composable
private fun PdvRedak(g: StavkaPdv) {
    val t = LocalFiskalTokens.current
    val stopa = g.stopa.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: g.stopa
    FiskalCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(t.oliveTint)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("$stopa%", color = t.oliveTintInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Osnovica ${hrEur(g.osnovica)}", style = MaterialTheme.typography.bodyMedium, color = t.ink, maxLines = 1)
                Text("Ukupno ${hrEur(g.ukupno)}", style = MaterialTheme.typography.bodySmall, color = t.muted, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("PDV", style = MaterialTheme.typography.labelMedium, color = t.muted)
                Text(hrEur(g.pdv), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Prodaja po artiklu: naziv, količina s jedinicom mjere te iznos desno. */
@Composable
private fun ArtiklRedak(a: StavkaArtikl) {
    val t = LocalFiskalTokens.current
    FiskalCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                a.naziv,
                style = MaterialTheme.typography.bodyMedium,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${hrKolicina(a.kolicina)} ${a.jedMjere}",
                style = MaterialTheme.typography.bodySmall,
                color = t.muted,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(10.dp))
            Text(hrEur(a.ukupno), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 1, softWrap = false)
        }
    }
}

/** Redak po načinu plaćanja s horizontalnom trakom proporcionalnom iznosu. BRAND-UPUTE 9.6. */
@Composable
private fun NacinPlacRedak(naziv: String, vrijednost: String, iznos: java.math.BigDecimal, max: java.math.BigDecimal) {
    val t = LocalFiskalTokens.current
    val udio = (iznos.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    FiskalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(naziv, style = MaterialTheme.typography.bodyMedium, color = t.ink, modifier = Modifier.weight(1f))
                Text(vrijednost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = t.muted)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(t.surfaceSunken, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(udio).height(6.dp).background(t.olive, RoundedCornerShape(3.dp)))
            }
        }
    }
}
