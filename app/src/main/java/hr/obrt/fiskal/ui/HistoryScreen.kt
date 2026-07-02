package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalChip
import hr.obrt.fiskal.ui.components.FiskalEmptyState
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.SearchPill
import hr.obrt.fiskal.ui.components.StatusBadge
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class FilterRacuna(val naziv: String) { SVI("Svi"), FISKALIZIRANI("Fiskalizirani"), NEFISKALIZIRANI("Nefiskalizirani") }

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (SavedInvoice) -> Unit, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    val ctx = LocalContext.current
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT) }
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FilterRacuna.SVI) }
    /** Datumski filter Od/Do (epoch millis, do razine minute); null = bez ograničenja. */
    var filterOd by remember { mutableStateOf<Long?>(null) }
    var filterDo by remember { mutableStateOf<Long?>(null) }

    val danas = pocetakDana()
    val mjesec = pocetakMjeseca()
    val prometDanas = zbroj(vm.history) { it.createdAt >= danas }
    val prometMjesec = zbroj(vm.history) { it.createdAt >= mjesec }

    val filtrirani = vm.history
        .filter { si ->
            when (filter) {
                FilterRacuna.SVI -> true
                FilterRacuna.FISKALIZIRANI -> si.jir != null
                FilterRacuna.NEFISKALIZIRANI -> si.jir == null
            }
        }
        .filter { si ->
            (filterOd?.let { si.createdAt >= it } ?: true) &&
                (filterDo?.let { si.createdAt <= it } ?: true)
        }
        .filter { si ->
            q.isBlank() ||
                si.brojRacuna().contains(q, true) ||
                (si.jir ?: "").contains(q, true) ||
                si.kupac.contains(q, true) ||
                FiskalFormat.amount(si.racun.iznosUkupno).contains(q)
        }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Računi", "${vm.history.size} računa", onBack = onBack)

        Column(Modifier.padding(horizontal = FiskalSpacing.screenX), verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap)) {
            FiskalCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(FiskalSpacing.card).fillMaxWidth()) {
                    PrometStavka("Promet danas", prometDanas, Modifier.weight(1f))
                    PrometStavka("Ovaj mjesec", prometMjesec, Modifier.weight(1f))
                }
            }

            SearchPill(q, { q = it }, "Pretraži broj, JIR, kupca, iznos…")

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterRacuna.entries.forEach { f ->
                    FiskalChip(f.naziv, filter == f) { filter = f }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { odaberiDatumVrijeme(ctx, filterOd ?: pocetakDana()) { filterOd = it } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        filterOd?.let { "Od: ${formatDatumVrijeme(it)}" } ?: "Od: —",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                OutlinedButton(
                    onClick = { odaberiDatumVrijeme(ctx, filterDo ?: System.currentTimeMillis()) { filterDo = it } },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        filterDo?.let { "Do: ${formatDatumVrijeme(it)}" } ?: "Do: —",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                if (filterOd != null || filterDo != null) {
                    TextButton(onClick = { filterOd = null; filterDo = null }) { Text("×") }
                }
            }
        }

        if (filtrirani.isEmpty()) {
            FiskalEmptyState(
                Icons.Rounded.ReceiptLong,
                if (vm.history.isEmpty()) "Još nema računa" else "Nema rezultata",
                if (vm.history.isEmpty()) "Novi račun kreiraš gumbom + u donjoj navigaciji." else "Pokušaj drugi pojam pretrage ili filter.",
                Modifier.padding(horizontal = FiskalSpacing.screenX),
            )
        } else {
            LazyColumn(
                Modifier.padding(horizontal = FiskalSpacing.screenX),
                verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
                contentPadding = PaddingValues(top = FiskalSpacing.stackGap, bottom = FiskalSpacing.listPad),
            ) {
                items(filtrirani) { si ->
                    FiskalCard(Modifier.fillMaxWidth(), onClick = { onOpen(si) }) {
                        Column(Modifier.padding(horizontal = FiskalSpacing.card, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Račun ${si.brojRacuna()}", style = MaterialTheme.typography.titleMedium, color = t.ink, modifier = Modifier.weight(1f))
                                Text(hrEur(si.racun.iznosUkupno), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = t.ink)
                            }
                            Text(
                                fmt.format(Date(si.createdAt)) + " · " + si.racun.nacinPlac.opis,
                                style = MaterialTheme.typography.bodySmall,
                                color = t.muted,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(si.jir != null)
                                Spacer(Modifier.weight(1f))
                                KrugAkcija(Icons.Rounded.Print) { ReceiptPrinter.print(ctx, vm.receiptFromSaved(si)) }
                                Spacer(Modifier.width(8.dp))
                                KrugAkcija(Icons.Rounded.Share) { InvoiceShare.sharePdf(ctx, vm.receiptFromSaved(si)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KrugAkcija(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val t = LocalFiskalTokens.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(t.oliveTint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = t.oliveTintInk, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun PrometStavka(naziv: String, iznos: BigDecimal, modifier: Modifier) {
    val t = LocalFiskalTokens.current
    Column(modifier) {
        Text(naziv, style = MaterialTheme.typography.labelMedium, color = t.muted)
        Text(hrEur(iznos), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.ink)
    }
}

private fun zbroj(list: List<SavedInvoice>, uvjet: (SavedInvoice) -> Boolean): BigDecimal =
    list.filter(uvjet).fold(BigDecimal.ZERO) { acc, si -> acc.add(si.racun.iznosUkupno) }
        .setScale(2, java.math.RoundingMode.HALF_UP)

private fun pocetakDana(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun pocetakMjeseca(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
