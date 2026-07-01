package hr.obrt.fiskal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.fiskal.BluetoothPrinter
import hr.obrt.fiskal.fiskal.EscPosReceiptBuilder
import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.QrRenderer
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(vm: AppViewModel, onBack: () -> Unit, onCopy: () -> Unit) {
    val si = vm.detail.value ?: return
    val ctx = LocalContext.current
    val data = remember(si.id) { vm.receiptFromSaved(si) }
    val qr = remember(si.id) { QrRenderer.toBitmap(si.qrUrl, 600).asImageBitmap() }
    val datum = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(Date(si.createdAt)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Račun ${si.brojRacuna()}") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Natrag") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary),
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(si.naslovTvrtke, style = MaterialTheme.typography.titleMedium) }
            item { Text("Datum: $datum", style = MaterialTheme.typography.bodySmall) }
            if (si.kupac.isNotBlank() || si.kupacOib.isNotBlank()) item {
                Text(
                    "Kupac: ${si.kupac}" + (if (si.kupacOib.isNotBlank()) " (OIB ${si.kupacOib})" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Divider() }
            items(si.racun.stavke) { s ->
                Column {
                    Row {
                        Text(s.naziv, Modifier.weight(1f))
                        Text("${FiskalFormat.amount(s.ukupno)} €")
                    }
                    if (si.racun.zaglavlje.uSustavuPdv) {
                        Text(
                            "neto ${FiskalFormat.amount(s.neto)} · PDV ${FiskalFormat.amount(s.pdvStopa)}% = ${FiskalFormat.amount(s.pdvIznos)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Divider()
                Text("UKUPNO: ${FiskalFormat.amount(si.racun.iznosUkupno)} €", style = MaterialTheme.typography.titleLarge)
                Text("Plaćanje: ${si.racun.nacinPlac.opis}", style = MaterialTheme.typography.bodySmall)
                if (si.napomena.isNotBlank()) Text("Napomena: ${si.napomena}", style = MaterialTheme.typography.bodySmall)
            }
            item { PoljeKopija("JIR", si.jir ?: "— (${si.status})", ctx) }
            item { PoljeKopija("ZKI", si.zki, ctx) }
            item { Image(bitmap = qr, contentDescription = "QR", modifier = Modifier.size(200.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ReceiptPrinter.print(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Ispiši / PDF") }
                    Button(onClick = { InvoiceShare.emailPdf(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Email") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { InvoiceShare.sharePdf(ctx, data) }, modifier = Modifier.weight(1f)) { Text("Podijeli") }
                    val printer = vm.printerAddressFor(si.companyId)
                    var isprint by remember { mutableStateOf(false) }
                    OutlinedButton(
                        enabled = printer != null && !isprint,
                        onClick = {
                            val addr = printer
                            if (addr != null) {
                                isprint = true
                                vm.viewModelScope.launch {
                                    val res = withContext(Dispatchers.IO) {
                                        BluetoothPrinter.posalji(ctx, addr, EscPosReceiptBuilder.build(data))
                                    }
                                    isprint = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (isprint) "Šaljem…" else "POS pisač") }
                }
            }
            item {
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(si.qrUrl) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Provjeri na Poreznoj") }
            }
            item {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                    Text("Kopiraj u novi račun")
                }
            }
            item {
                OutlinedButton(
                    onClick = { vm.obrisiIzPovijesti(si); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Obriši iz povijesti") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PoljeKopija(naziv: String, vrijednost: String, ctx: android.content.Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(naziv, style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(vrijednost, fontFamily = FontFamily.Monospace) }
        }
        IconButton(onClick = { InvoiceShare.copyToClipboard(ctx, naziv, vrijednost) }) {
            Icon(Icons.Filled.ContentCopy, "Kopiraj $naziv")
        }
    }
}
