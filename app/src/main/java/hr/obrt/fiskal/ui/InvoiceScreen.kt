package hr.obrt.fiskal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onArticles: () -> Unit,
    onPickArticle: () -> Unit,
) {
    val ishod = vm.ishod.value
    val tvrtka = vm.selected.value
    var meniOtvoren by remember { mutableStateOf(false) }
    var potvrdaIzlaza by remember { mutableStateOf(false) }
    var potvrdaFiskal by remember { mutableStateOf(false) }

    val izlaz: () -> Unit = { if (ishod == null && vm.imaUnos()) potvrdaIzlaza = true else onHome() }
    BackHandler { izlaz() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ishod != null) "Račun fiskaliziran" else "Novi račun", maxLines = 1) },
                navigationIcon = { TextButton(onClick = izlaz) { Text("Početna") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    TextButton(
                        onClick = onHistory,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) { Text("Računi") }
                    IconButton(onClick = { meniOtvoren = true }) { Icon(Icons.Filled.MoreVert, "Izbornik") }
                    DropdownMenu(expanded = meniOtvoren, onDismissRequest = { meniOtvoren = false }) {
                        DropdownMenuItem(text = { Text("Šifrarnik artikala") }, onClick = { meniOtvoren = false; onArticles() })
                        DropdownMenuItem(text = { Text("Postavke tvrtke") }, onClick = { meniOtvoren = false; onSettings() })
                    }
                },
            )
        },
        floatingActionButton = {
            if (ishod == null) ExtendedFloatingActionButton(
                onClick = { if (!vm.ucitavanje.value) potvrdaFiskal = true },
                icon = { if (vm.ucitavanje.value) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                text = { Text(if (vm.ucitavanje.value) "Šaljem…" else "Fiskaliziraj") },
            )
        },
    ) { pad ->
        if (ishod != null) ResultView(vm, Modifier.padding(pad))
        else InvoiceForm(vm, tvrtka, Modifier.padding(pad), onPickArticle)
    }

    if (potvrdaIzlaza) AlertDialog(
        onDismissRequest = { potvrdaIzlaza = false },
        title = { Text("Napustiti račun?") },
        text = { Text("Uneseni podaci nisu fiskalizirani i neće biti spremljeni.") },
        confirmButton = { TextButton(onClick = { potvrdaIzlaza = false; onHome() }) { Text("Napusti") } },
        dismissButton = { TextButton(onClick = { potvrdaIzlaza = false }) { Text("Ostani") } },
    )

    if (potvrdaFiskal) {
        val produkcija = tvrtka?.okolina == hr.obrt.fiskal.fiskal.FiskalOkolina.PRODUKCIJA
        AlertDialog(
            onDismissRequest = { potvrdaFiskal = false },
            title = { Text(if (vm.storno.value) "Fiskalizirati STORNO?" else "Fiskalizirati račun?") },
            text = {
                Column {
                    Text("Stavki: ${vm.stavke.count { it.naziv.isNotBlank() }} · Ukupno: ${vm.ukupno().toPlainString()} €")
                    if (vm.kupacNaziv.value.isNotBlank()) Text("Kupac: ${vm.kupacNaziv.value}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (produkcija) "PRODUKCIJA — ovo je PRAVA fiskalizacija." else "TEST okolina.",
                        color = if (produkcija) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { potvrdaFiskal = false; vm.fiskaliziraj() }) { Text("Fiskaliziraj") } },
            dismissButton = { TextButton(onClick = { potvrdaFiskal = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun InvoiceForm(vm: AppViewModel, tvrtka: hr.obrt.fiskal.data.Tvrtka?, modifier: Modifier, onPickArticle: () -> Unit) {
    val pdv = tvrtka?.uSustavuPdv == true
    var prikaziKupca by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text("${tvrtka?.okolina?.opis ?: "—"} · sljedeći br. ${tvrtka?.sljedeciBroj ?: 0}/${tvrtka?.oznPosPr ?: ""}/${tvrtka?.oznNapUr ?: ""}")
                },
            )
        }

        itemsIndexed(vm.stavke) { index, _ -> StavkaKartica(vm, index, pdv) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.dodajStavku() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Stavka")
                }
                FilledTonalButton(onClick = onPickArticle, modifier = Modifier.weight(1f)) { Text("Iz šifrarnika") }
            }
        }

        item { KupacNapomena(vm, prikaziKupca) { prikaziKupca = it } }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.storno.value, onCheckedChange = { vm.storno.value = it })
                Spacer(Modifier.width(8.dp))
                Text("Storno (iznosi u minus)")
            }
        }

        item { SazetakKartica(vm, pdv) }

        vm.greska.value?.let { g ->
            item { Text(g, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun StavkaKartica(vm: AppViewModel, index: Int, pdv: Boolean) {
    val s = vm.stavke[index]
    ElevatedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Stavka ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.ukloniStavku(index) }) {
                    Icon(Icons.Filled.Delete, "Ukloni", tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = s.naziv, onValueChange = { vm.setNaziv(index, it) },
                label = { Text("Naziv") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Polje(s.kolicina, "Količina", Modifier.weight(1f)) { vm.setKolicina(index, it) }
                Polje(s.jedCijena, if (pdv) "Cijena (neto)" else "Cijena", Modifier.weight(1f)) { vm.setJedCijena(index, it) }
            }
            if (pdv) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Polje(s.pdvStopa, "PDV %", Modifier.weight(1f)) { vm.setStopa(index, it) }
                    Polje(s.neto, "Neto", Modifier.weight(1f)) { vm.setNeto(index, it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Polje(s.pdvIznos, "PDV €", Modifier.weight(1f)) { vm.setPdvIznos(index, it) }
                    Polje(s.ukupno, "Ukupno", Modifier.weight(1f)) { vm.setUkupno(index, it) }
                }
            }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ukupno stavke", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${s.ukupno.ifBlank { "0.00" }} €",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun KupacNapomena(vm: AppViewModel, prosiren: Boolean, naProsiri: (Boolean) -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Kupac i napomena (nije obavezno)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { naProsiri(!prosiren) }) { Text(if (prosiren) "Sakrij" else "Dodaj") }
            }
            if (prosiren) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(vm.kupacNaziv.value, { vm.kupacNaziv.value = it }, label = { Text("Naziv kupca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        vm.kupacOib.value, { vm.kupacOib.value = it.filter(Char::isDigit).take(11) },
                        label = { Text("OIB kupca") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(vm.napomena.value, { vm.napomena.value = it }, label = { Text("Napomena") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SazetakKartica(vm: AppViewModel, pdv: Boolean) {
    val crveno = vm.storno.value
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (crveno) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pdv) {
                SazetakRedak("Neto", "${vm.zbrojNeto().toPlainString()} €")
                SazetakRedak("PDV", "${vm.zbrojPdv().toPlainString()} €")
            }
            Row {
                Text("UKUPNO", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${vm.ukupno().toPlainString()} €", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SazetakRedak(naziv: String, vrijednost: String) {
    Row {
        Text(naziv, modifier = Modifier.weight(1f))
        Text(vrijednost)
    }
}

@Composable
private fun Polje(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = modifier,
    )
}

@Composable
private fun ResultView(vm: AppViewModel, modifier: Modifier) {
    val ishod = vm.ishod.value ?: return
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val data = vm.receiptFromIshod()

    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            when (val r = ishod.rezultat) {
                is FiskalRezultat.Uspjeh -> StatusKartica("✓ Račun je fiskaliziran (JIR dodijeljen).", MaterialTheme.colorScheme.secondaryContainer)
                is FiskalRezultat.Greska -> StatusKartica("✗ CIS je odbio račun.\nŠifra: ${r.sifra}\n${r.poruka}\n\nRačun NIJE fiskaliziran. Ispravi i pokušaj ponovno.", MaterialTheme.colorScheme.errorContainer)
                is FiskalRezultat.Neizvjesno -> StatusKartica("⚠ NEIZVJESNO: ${r.poruka}\n\nOdgovor je stigao, ali JIR nije pročitan. PRIJE ponovne fiskalizacije provjeri ZKI (dolje) na porezna.gov.hr — ako se račun nađe, fiskaliziran je.", MaterialTheme.colorScheme.errorContainer)
                is FiskalRezultat.Mreza -> StatusKartica("⚠ Nije poslano: ${r.poruka}\n\nRačun nije stigao do CIS-a (sigurno NIJE fiskaliziran). ZKI je izračunat; po potrebi otisni i naknadno dostavi.", MaterialTheme.colorScheme.errorContainer)
            }
        }
        (ishod.rezultat as? FiskalRezultat.Neizvjesno)?.let { r ->
            if (r.rawOdgovor.isNotBlank()) item {
                var prikaziRaw by remember { mutableStateOf(false) }
                TextButton(onClick = { prikaziRaw = !prikaziRaw }) { Text(if (prikaziRaw) "Sakrij odgovor poslužitelja" else "Prikaži odgovor poslužitelja") }
                if (prikaziRaw) SelectionContainer { Text(r.rawOdgovor, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
        item { Polje2("JIR", ishod.jir ?: "— (nije dodijeljen)") }
        item { Polje2("ZKI", ishod.zki) }
        item {
            Text("QR kôd (provjera računa)", style = MaterialTheme.typography.labelLarge)
            val bmp = remember(ishod.qrUrl) { QrRenderer.toBitmap(ishod.qrUrl, 600).asImageBitmap() }
            Image(bmp, "QR", Modifier.size(220.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { data?.let { ReceiptPrinter.print(ctx, it) } }, modifier = Modifier.weight(1f)) { Text("Ispiši / PDF") }
                Button(onClick = { data?.let { InvoiceShare.emailPdf(ctx, it) } }, modifier = Modifier.weight(1f)) { Text("Email") }
            }
        }
        item {
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(ishod.qrUrl) } }, modifier = Modifier.fillMaxWidth()) { Text("Provjeri na Poreznoj") }
        }
        item {
            FilledTonalButton(onClick = { vm.resetRacun() }, modifier = Modifier.fillMaxWidth()) { Text("Novi račun") }
        }
    }
}

@Composable
private fun StatusKartica(naslov: String, boja: androidx.compose.ui.graphics.Color) {
    Card(colors = CardDefaults.cardColors(containerColor = boja)) {
        Text(naslov, Modifier.padding(16.dp), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Polje2(naziv: String, vrijednost: String) {
    Column {
        Text(naziv, style = MaterialTheme.typography.labelLarge)
        SelectionContainer { Text(vrijednost, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge) }
    }
}
