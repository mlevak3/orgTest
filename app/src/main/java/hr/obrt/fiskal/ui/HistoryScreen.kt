package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (SavedInvoice) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT) }
    var q by remember { mutableStateOf("") }

    val danas = pocetakDana()
    val mjesec = pocetakMjeseca()
    val prometDanas = zbroj(vm.history) { it.jir != null && it.createdAt >= danas }
    val prometMjesec = zbroj(vm.history) { it.jir != null && it.createdAt >= mjesec }

    val filtrirani = vm.history.filter { si ->
        q.isBlank() ||
            si.brojRacuna().contains(q, true) ||
            (si.jir ?: "").contains(q, true) ||
            si.kupac.contains(q, true) ||
            FiskalFormat.amount(si.racun.iznosUkupno).contains(q)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Računi — ${vm.selected.value?.opis() ?: ""}", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Početna") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer),
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth()) {
                    PrometStavka("Promet danas", prometDanas, Modifier.weight(1f))
                    PrometStavka("Ovaj mjesec", prometMjesec, Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                value = q, onValueChange = { q = it },
                label = { Text("Pretraži (broj, JIR, kupac, iznos)") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            if (filtrirani.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (vm.history.isEmpty()) "Još nema spremljenih računa." else "Nema rezultata za pretragu.")
                }
            } else {
                val ctx = LocalContext.current
                LazyColumn(
                    Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filtrirani) { si ->
                        ElevatedCard(onClick = { onOpen(si) }) {
                            Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Račun ${si.brojRacuna()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${FiskalFormat.amount(si.racun.iznosUkupno)} €", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Text(fmt.format(Date(si.createdAt)), style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusCip(si)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { ReceiptPrinter.print(ctx, vm.receiptFromSaved(si)) }) {
                                        Icon(Icons.Filled.Print, "Ispiši", modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { InvoiceShare.sharePdf(ctx, vm.receiptFromSaved(si)) }) {
                                        Icon(Icons.Filled.Share, "Podijeli", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrometStavka(naziv: String, iznos: BigDecimal, modifier: Modifier) {
    Column(modifier) {
        Text(naziv, style = MaterialTheme.typography.labelMedium)
        Text("${iznos.toPlainString()} €", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusCip(si: SavedInvoice) {
    val (tekst, boja) = when {
        si.jir != null -> "Fiskaliziran ✓" to Color(0xFF2E7D32)
        si.status.startsWith("NEIZVJESNO") -> "Neizvjesno — provjeri" to Color(0xFFB26A00)
        else -> "Nije fiskaliziran" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(tekst) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = boja,
            disabledContainerColor = boja.copy(alpha = 0.12f),
        ),
        border = null,
    )
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
