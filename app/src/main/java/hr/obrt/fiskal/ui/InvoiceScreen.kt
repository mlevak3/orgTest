package hr.obrt.fiskal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.fiskal.FiskalRezultat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.QrRenderer
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import hr.obrt.fiskal.model.NacinPlac

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    vm: AppViewModel,
    onCompanies: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val ishod = vm.ishod.value
    val ucitavanje = vm.ucitavanje.value
    val tvrtka = vm.selected.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tvrtka?.opis() ?: "Fiskalizacija") },
                navigationIcon = { TextButton(onClick = onCompanies) { Text("Tvrtke") } },
                actions = {
                    TextButton(onClick = onHistory) { Text("Računi") }
                    TextButton(onClick = onSettings) { Text("Postavke") }
                },
            )
        }
    ) { pad ->
        if (ishod != null) {
            ResultView(vm, Modifier.padding(pad))
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Okolina: ${tvrtka?.okolina?.opis ?: "—"} · Račun br. " +
                        "${tvrtka?.sljedeciBroj ?: 0}/${tvrtka?.oznPosPr ?: ""}/${tvrtka?.oznNapUr ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            itemsIndexed(vm.stavke) { index, _ ->
                StavkaRedak(vm, index)
            }

            item {
                OutlinedButton(onClick = { vm.dodajStavku() }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Dodaj stavku")
                }
            }

            item {
                Text("Način plaćanja", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NacinPlac.entries.forEach { np ->
                        FilterChip(
                            selected = vm.nacinPlac.value == np,
                            onClick = { vm.nacinPlac.value = np },
                            label = { Text(np.oznaka) },
                        )
                    }
                }
                Text(vm.nacinPlac.value.opis, style = MaterialTheme.typography.bodySmall)
            }

            item {
                Divider()
                Text(
                    "UKUPNO: ${vm.ukupno().toPlainString()} EUR",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            vm.greska.value?.let { g ->
                item {
                    Text(g, color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                Button(
                    onClick = { vm.fiskaliziraj() },
                    enabled = !ucitavanje,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ucitavanje) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Fiskaliziram…")
                    } else {
                        Text("FISKALIZIRAJ")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StavkaRedak(vm: AppViewModel, index: Int) {
    val s = vm.stavke[index]
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = s.naziv,
                    onValueChange = { vm.azurirajStavku(index, s.copy(naziv = it)) },
                    label = { Text("Naziv stavke") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.ukloniStavku(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Ukloni")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = s.kolicina,
                    onValueChange = { vm.azurirajStavku(index, s.copy(kolicina = it)) },
                    label = { Text("Količina") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = s.cijena,
                    onValueChange = { vm.azurirajStavku(index, s.copy(cijena = it)) },
                    label = { Text("Cijena (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (vm.selected.value?.uSustavuPdv == true) {
                    OutlinedTextField(
                        value = s.pdvStopa,
                        onValueChange = { vm.azurirajStavku(index, s.copy(pdvStopa = it)) },
                        label = { Text("PDV %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultView(vm: AppViewModel, modifier: Modifier) {
    val ishod = vm.ishod.value ?: return
    LazyColumn(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            when (val r = ishod.rezultat) {
                is FiskalRezultat.Uspjeh -> StatusKartica(
                    naslov = "✓ Račun je fiskaliziran (JIR dodijeljen).",
                    boja = MaterialTheme.colorScheme.secondary,
                )
                is FiskalRezultat.Greska -> StatusKartica(
                    naslov = "✗ CIS je odbio račun.\nŠifra: ${r.sifra}\n${r.poruka}\n\n" +
                        "Račun NIJE fiskaliziran. Ispravi i pokušaj ponovno.",
                    boja = MaterialTheme.colorScheme.error,
                )
                is FiskalRezultat.Neizvjesno -> StatusKartica(
                    naslov = "⚠ NEIZVJESNO: ${r.poruka}\n\n" +
                        "Odgovor je stigao, ali JIR nije pročitan. PRIJE ponovne fiskalizacije " +
                        "provjeri ZKI (dolje) na porezna.gov.hr/rn — ako se račun nađe, fiskaliziran je.",
                    boja = MaterialTheme.colorScheme.error,
                )
                is FiskalRezultat.Mreza -> StatusKartica(
                    naslov = "⚠ Nije poslano: ${r.poruka}\n\n" +
                        "Račun nije stigao do CIS-a (sigurno NIJE fiskaliziran). " +
                        "ZKI je izračunat; po potrebi otisni i naknadno dostavi.",
                    boja = MaterialTheme.colorScheme.error,
                )
            }
        }
        (ishod.rezultat as? FiskalRezultat.Neizvjesno)?.let { r ->
            if (r.rawOdgovor.isNotBlank()) item {
                var prikaziRaw by remember { mutableStateOf(false) }
                TextButton(onClick = { prikaziRaw = !prikaziRaw }) {
                    Text(if (prikaziRaw) "Sakrij odgovor poslužitelja" else "Prikaži odgovor poslužitelja")
                }
                if (prikaziRaw) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(r.rawOdgovor, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        item { Polje("JIR", ishod.jir ?: "— (nije dodijeljen)") }
        item { Polje("ZKI", ishod.zki) }
        item {
            Text("QR kôd (provjera računa)", style = MaterialTheme.typography.labelLarge)
            QrSlika(ishod.qrUrl)
        }
        item {
            val ctx = LocalContext.current
            val data = vm.receiptFromIshod()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { data?.let { ReceiptPrinter.print(ctx, it) } },
                    modifier = Modifier.weight(1f),
                ) { Text("Ispiši / PDF") }
                Button(
                    onClick = { data?.let { InvoiceShare.emailPdf(ctx, it) } },
                    modifier = Modifier.weight(1f),
                ) { Text("Email") }
            }
        }
        item {
            OutlinedButton(onClick = { vm.resetRacun() }, modifier = Modifier.fillMaxWidth()) {
                Text("Novi račun")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QrSlika(qrUrl: String) {
    val bitmap = remember(qrUrl) { QrRenderer.toBitmap(qrUrl, size = 600).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = "QR kôd računa",
        modifier = Modifier.size(220.dp),
    )
}

@Composable
private fun StatusKartica(naslov: String, boja: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = boja.copy(alpha = 0.12f))) {
        Text(naslov, Modifier.padding(14.dp), color = boja, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Polje(naziv: String, vrijednost: String) {
    Column {
        Text(naziv, style = MaterialTheme.typography.labelLarge)
        SelectionText(vrijednost)
    }
}

@Composable
private fun SelectionText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
    }
}
