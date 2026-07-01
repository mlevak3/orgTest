@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hr.obrt.fiskal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.fiskal.BluetoothPrinter
import hr.obrt.fiskal.fiskal.EscPosReceiptBuilder
import hr.obrt.fiskal.fiskal.FiskalRezultat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.QrRenderer
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import hr.obrt.fiskal.model.NacinPlac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    vm: AppViewModel,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onArticles: () -> Unit,
    onPickArticle: () -> Unit,
    onPartners: () -> Unit,
    onPickPartner: () -> Unit,
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
                        DropdownMenuItem(text = { Text("Šifrarnik partnera") }, onClick = { meniOtvoren = false; onPartners() })
                        DropdownMenuItem(text = { Text("Postavke tvrtke") }, onClick = { meniOtvoren = false; onSettings() })
                    }
                },
            )
        },
        floatingActionButton = {
            if (ishod == null) ExtendedFloatingActionButton(
                onClick = { if (!vm.ucitavanje.value) potvrdaFiskal = true },
                icon = { if (vm.ucitavanje.value) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                text = { Text(if (vm.ucitavanje.value) "Šaljem…" else "Kreiraj račun") },
            )
        },
    ) { pad ->
        if (ishod != null) ResultView(vm, Modifier.padding(pad))
        else InvoiceForm(vm, tvrtka, Modifier.padding(pad), onPickArticle, onPickPartner)
    }

    if (vm.stavkaUnos.value != null) StavkaUnosDijalog(vm, tvrtka?.uSustavuPdv == true)

    if (potvrdaIzlaza) AlertDialog(
        onDismissRequest = { potvrdaIzlaza = false },
        title = { Text("Napustiti račun?") },
        text = { Text("Uneseni podaci nisu fiskalizirani i neće biti spremljeni.") },
        confirmButton = { TextButton(onClick = { potvrdaIzlaza = false; onHome() }) { Text("Napusti") } },
        dismissButton = { TextButton(onClick = { potvrdaIzlaza = false }) { Text("Ostani") } },
    )

    if (potvrdaFiskal) {
        val produkcija = tvrtka?.okolina == hr.obrt.fiskal.fiskal.FiskalOkolina.PRODUKCIJA
        val jeStorno = vm.ukupno() < java.math.BigDecimal.ZERO
        AlertDialog(
            onDismissRequest = { potvrdaFiskal = false },
            title = { Text(if (jeStorno) "Kreirati STORNO račun?" else "Kreirati račun?") },
            text = {
                Column {
                    Text("Stavki: ${vm.stavke.size} · Ukupno: ${vm.ukupno().toPlainString()} €")
                    if (vm.kupacNaziv.value.isNotBlank()) Text("Kupac: ${vm.kupacNaziv.value}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (produkcija) "PRODUKCIJA — ovo je PRAVA fiskalizacija." else "TEST okolina.",
                        color = if (produkcija) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { potvrdaFiskal = false; vm.fiskaliziraj() }) { Text("Kreiraj račun") } },
            dismissButton = { TextButton(onClick = { potvrdaFiskal = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun InvoiceForm(
    vm: AppViewModel,
    tvrtka: hr.obrt.fiskal.data.Tvrtka?,
    modifier: Modifier,
    onPickArticle: () -> Unit,
    onPickPartner: () -> Unit,
) {
    val pdv = tvrtka?.uSustavuPdv == true
    var prikaziKupca by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item {
            val d = vm.selectedDjelatnost.value
            val p = vm.selectedProstor.value
            val u = vm.selectedUredjaj.value
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        "${tvrtka?.okolina?.opis ?: "—"}" +
                            (d?.let { " · ${it.opis()}" } ?: "") +
                            " · br. ${vm.brojRacuna.value}/${p?.oznaka ?: ""}/${u?.oznaka ?: ""}"
                    )
                },
            )
        }

        item { KupacNapomena(vm, prikaziKupca, onPickPartner) { prikaziKupca = it } }

        if (vm.stavke.isEmpty()) {
            item {
                Text(
                    "Nema stavki.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(vm.stavke) { index, _ -> StavkaRedak(vm, index, pdv) }

        item {
            FilledTonalButton(onClick = onPickArticle, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dodaj novu stavku računa")
            }
        }

        item {
            Text("Način plaćanja", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Polje(vm.popustRacuna.value, "Popust na račun (%)", Modifier.fillMaxWidth()) { vm.setPopustRacuna(it) }
        }

        item { SazetakKartica(vm, pdv) }

        vm.greska.value?.let { g ->
            item { Text(g, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Redak potvrđene stavke — read-only prikaz (tap za uređivanje, ikona za brisanje). */
@Composable
private fun StavkaRedak(vm: AppViewModel, index: Int, pdv: Boolean) {
    val s = vm.stavke[index]
    ElevatedCard(onClick = { vm.zapocniUredjivanjeStavke(index) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.naziv, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.kolicina} ${s.jedMjere} × ${s.jedCijena.ifBlank { "0.00" }} €" +
                        (if (pdv) " · PDV ${s.pdvStopa}%" else "") +
                        (s.popust.toBigDecimalOrNull()?.let { if (it.signum() != 0) " · popust ${s.popust}%" else "" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${s.ukupno.ifBlank { "0.00" }} €",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { vm.ukloniStavku(index) }) {
                Icon(Icons.Filled.Delete, "Ukloni", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Dijalog za dodavanje/uređivanje stavke — naziv/jed.mjere/PDV% su zaključani (iz artikla), mijenja se samo količina i cijena. */
@Composable
private fun StavkaUnosDijalog(vm: AppViewModel, pdv: Boolean) {
    val u = vm.stavkaUnos.value ?: return
    val ukupno = vm.izracunUnosa()
    val uredjivanje = vm.uredjivanjeIndex.value != null
    AlertDialog(
        onDismissRequest = { vm.otkaziUnosStavke() },
        title = { Text(if (uredjivanje) "Uredi stavku" else "Nova stavka") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(u.naziv, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Jed. mjere: ${u.jedMjere}" + (if (pdv) " · PDV: ${u.pdvStopa}%" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Polje(u.kolicina, "Količina", Modifier.weight(1f)) { vm.setUnosKolicina(it) }
                    Polje(u.jedCijena, if (pdv) "Cijena (neto)" else "Cijena", Modifier.weight(1f)) { vm.setUnosCijena(it) }
                }
                Polje(u.popust, "Popust (%)", Modifier.fillMaxWidth()) { vm.setUnosPopust(it) }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ukupno stavke", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${ukupno?.toPlainString() ?: "0.00"} €",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.potvrdiUnosStavke() }) { Text(if (uredjivanje) "Spremi" else "Dodaj") } },
        dismissButton = { TextButton(onClick = { vm.otkaziUnosStavke() }) { Text("Odustani") } },
    )
}

@Composable
private fun KupacNapomena(vm: AppViewModel, prosiren: Boolean, onPickPartner: () -> Unit, naProsiri: (Boolean) -> Unit) {
    val imaKupca = vm.kupacNaziv.value.isNotBlank()
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Kupac", style = MaterialTheme.typography.titleSmall)
                    if (imaKupca) Text(
                        vm.kupacNaziv.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ) else Text(
                        "Nije obavezno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { naProsiri(!prosiren) }) { Text(if (prosiren) "Sakrij" else if (imaKupca) "Uredi" else "Dodaj") }
            }
            if (prosiren) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "Krajnji kupac (upiši ručno) ili odaberi predefiniranog partnera:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onPickPartner, modifier = Modifier.fillMaxWidth()) { Text("Odaberi partnera") }
                    OutlinedTextField(vm.kupacNaziv.value, { vm.kupacNaziv.value = it }, label = { Text("Naziv kupca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        vm.kupacOib.value, { vm.kupacOib.value = it.filter(Char::isDigit).take(11) },
                        label = { Text("OIB kupca") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(vm.kupacAdresa.value, { vm.kupacAdresa.value = it }, label = { Text("Adresa kupca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(vm.napomena.value, { vm.napomena.value = it }, label = { Text("Napomena") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SazetakKartica(vm: AppViewModel, pdv: Boolean) {
    val crveno = vm.ukupno() < java.math.BigDecimal.ZERO
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
        item { Polje2("JIR", ishod.jir ?: "— (nije dodijeljen)", ctx) }
        item { Polje2("ZKI", ishod.zki, ctx) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { data?.let { InvoiceShare.sharePdf(ctx, it) } }, modifier = Modifier.weight(1f)) { Text("Podijeli") }
                val printer = vm.printerAddress()
                var isprint by remember { mutableStateOf(false) }
                OutlinedButton(
                    enabled = printer != null && !isprint,
                    onClick = {
                        val d = data; val addr = printer
                        if (d != null && addr != null) {
                            isprint = true
                            vm.viewModelScope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    BluetoothPrinter.posalji(ctx, addr, EscPosReceiptBuilder.build(d))
                                }
                                isprint = false
                                vm.greska.value = res.fold({ null }, { "Ispis na pisač nije uspio: ${it.message}" })
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (isprint) "Šaljem…" else "POS pisač") }
            }
            if (vm.printerAddress() == null) Text(
                "Bluetooth pisač nije postavljen (Postavke tvrtke).",
                style = MaterialTheme.typography.bodySmall,
            )
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
private fun Polje2(naziv: String, vrijednost: String, ctx: android.content.Context) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(naziv, style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(vrijednost, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge) }
        }
        IconButton(onClick = { InvoiceShare.copyToClipboard(ctx, naziv, vrijednost) }) {
            Icon(Icons.Filled.ContentCopy, "Kopiraj $naziv")
        }
    }
}
