package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.fiskal.BluetoothPrinter
import hr.obrt.fiskal.fiskal.EscPosReceiptBuilder
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalOutlineButton
import hr.obrt.fiskal.ui.components.FiskalPrimaryButton
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.StatusBadge
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InvoiceDetailScreen(vm: AppViewModel, onBack: () -> Unit, onCopy: () -> Unit, onStorno: () -> Unit) {
    val t = LocalFiskalTokens.current
    val si = vm.detail.value ?: return
    val ctx = LocalContext.current
    val data = remember(si) { vm.receiptFromSaved(si) }
    val datum = remember(si) { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(Date(si.createdAt)) }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Račun ${si.brojRacuna()}", si.naslovTvrtke, onBack = onBack)

        LazyColumn(
            Modifier.padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
            contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
        ) {
            item {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(FiskalSpacing.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Datum: $datum", style = MaterialTheme.typography.bodySmall, color = t.muted, modifier = Modifier.weight(1f))
                            StatusBadge(si.jir != null)
                        }
                        if (si.kupac.isNotBlank() || si.kupacOib.isNotBlank()) {
                            Text(
                                "Kupac: ${si.kupac}" + (if (si.kupacOib.isNotBlank()) " (OIB ${si.kupacOib})" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.ink,
                            )
                            if (si.kupacAdresa.isNotBlank()) Text(si.kupacAdresa, style = MaterialTheme.typography.bodySmall, color = t.muted)
                        }
                    }
                }
            }

            item {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(FiskalSpacing.card)) {
                        si.racun.stavke.forEachIndexed { index, s ->
                            val stopa = s.pdvStopa.stripTrailingZeros().toPlainString()
                            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.naziv, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = t.ink)
                                    Text(hrEur(s.ukupno), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = t.ink)
                                }
                                Text(
                                    "${hrKolicina(s.kolicina)} ${s.jedMjere} × ${hrEur(s.jedinicnaCijena())}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.muted,
                                )
                                if (si.racun.zaglavlje.uSustavuPdv) {
                                    Text(
                                        "Osnovica ${hrEur(s.neto)} · PDV $stopa% = ${hrEur(s.pdvIznos)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = t.muted,
                                    )
                                }
                            }
                            if (index < si.racun.stavke.lastIndex) androidx.compose.material3.Divider(color = t.border)
                        }
                        androidx.compose.material3.Divider(color = t.border, modifier = Modifier.padding(vertical = 8.dp))
                        Text("UKUPNO: ${hrEur(si.racun.iznosUkupno)}", style = MaterialTheme.typography.titleLarge, color = t.ink)
                        Text("Plaćanje: ${si.racun.nacinPlac.opis}", style = MaterialTheme.typography.bodySmall, color = t.muted)
                        if (si.napomena.isNotBlank()) Text("Napomena: ${si.napomena}", style = MaterialTheme.typography.bodySmall, color = t.muted)
                    }
                }
            }

            item {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(FiskalSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PoljeKopija("JIR", si.jir ?: "— (${si.status})", ctx)
                        PoljeKopija("ZKI", si.zki, ctx)
                    }
                }
            }

            if (si.jir == null) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Račun nije fiskaliziran (nema JIR-a).",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.error,
                    )
                    FiskalPrimaryButton(
                        if (vm.ucitavanje.value) "Šaljem…" else "Pokušaj ponovno (fiskaliziraj)",
                        onClick = { vm.ponoviFiskalizaciju(si) },
                        enabled = !vm.ucitavanje.value,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    vm.greska.value?.let { Text(it, color = t.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ReceiptPrinter.print(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Ispiši / PDF") }
                    Button(onClick = { InvoiceShare.emailPdf(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Email") }
                }
            }
            item {
                val printer = vm.printerAddressFor(si.companyId)
                var isprint by remember { mutableStateOf(false) }
                var printPoruka by remember { mutableStateOf<String?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { InvoiceShare.sharePdf(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Podijeli") }
                    OutlinedButton(
                        enabled = printer != null && !isprint,
                        onClick = {
                            val addr = printer
                            if (addr != null) {
                                isprint = true
                                printPoruka = null
                                vm.viewModelScope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        BluetoothPrinter.posalji(ctx, addr, EscPosReceiptBuilder.build(data))
                                    }
                                    isprint = false
                                    printPoruka = res.fold({ "Poslano na pisač." }, { "Ispis nije uspio: ${it.message}" })
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isprint) "Šaljem…" else "POS pisač") }
                }
                printPoruka?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.muted) }
            }
            item {
                val uriHandler = LocalUriHandler.current
                FiskalOutlineButton(
                    "Provjeri na Poreznoj",
                    onClick = { runCatching { uriHandler.openUri(si.qrUrl) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FiskalOutlineButton("Kopiraj u novi račun", onClick = onCopy, modifier = Modifier.fillMaxWidth())
            }
            if (si.jir != null) item {
                FiskalOutlineButton("Storniraj račun", onClick = onStorno, destruktivno = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                FiskalOutlineButton(
                    "Obriši iz povijesti",
                    onClick = { vm.obrisiIzPovijesti(si); onBack() },
                    destruktivno = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PoljeKopija(naziv: String, vrijednost: String, ctx: android.content.Context) {
    val t = LocalFiskalTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(naziv, style = MaterialTheme.typography.labelMedium, color = t.muted)
            SelectionContainer { Text(vrijednost, fontFamily = FontFamily.Monospace, color = t.ink) }
        }
        IconButton(onClick = { InvoiceShare.copyToClipboard(ctx, naziv, vrijednost) }) {
            Icon(Icons.Rounded.ContentCopy, "Kopiraj $naziv", tint = t.mutedSoft)
        }
    }
}
