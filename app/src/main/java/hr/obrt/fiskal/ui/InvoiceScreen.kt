@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hr.obrt.fiskal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
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
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalChip
import hr.obrt.fiskal.ui.components.FiskalTerracottaButton
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.QuantityStepper
import hr.obrt.fiskal.ui.components.SearchPill
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

@Composable
fun InvoiceScreen(
    vm: AppViewModel,
    onHome: () -> Unit,
    onPickPartner: () -> Unit,
) {
    val t = LocalFiskalTokens.current
    val ishod = vm.ishod.value
    val tvrtka = vm.selected.value
    var potvrdaIzlaza by remember { mutableStateOf(false) }
    var potvrdaFiskal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadArticles() }

    val izlaz: () -> Unit = { if (ishod == null && vm.imaUnos()) potvrdaIzlaza = true else onHome() }
    BackHandler { izlaz() }

    val p = vm.selectedProstor.value
    val u = vm.selectedUredjaj.value

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader(
            "Novi račun",
            "Račun ${vm.brojRacuna.value}/${p?.oznaka ?: ""}/${u?.oznaka ?: ""} · ${tvrtka?.opis() ?: ""}",
            onBack = izlaz,
        )
        if (ishod != null) ResultView(vm, Modifier.weight(1f))
        else InvoiceForm(vm, tvrtka, Modifier.weight(1f), onPickPartner) { potvrdaFiskal = true }
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
        val jeStorno = vm.ukupno() < BigDecimal.ZERO
        AlertDialog(
            onDismissRequest = { potvrdaFiskal = false },
            title = { Text(if (jeStorno) "Naplatiti STORNO račun?" else "Naplatiti račun?") },
            text = {
                Column {
                    Text("Stavki: ${vm.stavke.size} · Ukupno: ${hrEur(vm.ukupno())}")
                    if (vm.kupacNaziv.value.isNotBlank()) Text("Kupac: ${vm.kupacNaziv.value}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (produkcija) "PRODUKCIJA — ovo je PRAVA fiskalizacija." else "TEST okolina.",
                        color = if (produkcija) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { potvrdaFiskal = false; vm.fiskaliziraj() }) { Text("Naplati") } },
            dismissButton = { TextButton(onClick = { potvrdaFiskal = false }) { Text("Odustani") } },
        )
    }
}

@Composable
private fun InvoiceForm(
    vm: AppViewModel,
    tvrtka: hr.obrt.fiskal.data.Tvrtka?,
    modifier: Modifier,
    onPickPartner: () -> Unit,
    onNaplati: () -> Unit,
) {
    val t = LocalFiskalTokens.current
    val pdv = tvrtka?.uSustavuPdv == true
    var prikaziKupca by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    val filtriraniArtikli = vm.articles.filter { searchQ.isBlank() || it.naziv.contains(searchQ, true) }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
            contentPadding = PaddingValues(top = FiskalSpacing.stackGap, bottom = 16.dp),
        ) {
            item { KupacNapomena(vm, prikaziKupca, onPickPartner) { prikaziKupca = it } }

            item { SearchPill(searchQ, { searchQ = it }, "Pretraži artikle…") }

            if (filtriraniArtikli.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(FiskalSpacing.gridGap)) {
                    filtriraniArtikli.chunked(2).forEach { par ->
                        Row(horizontalArrangement = Arrangement.spacedBy(FiskalSpacing.gridGap)) {
                            par.forEach { a -> ArtiklTile(a, Modifier.weight(1f)) { vm.dodajIliPovecajStavku(a) } }
                            if (par.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Text("Stavke · ${vm.stavke.size}", style = MaterialTheme.typography.titleSmall, color = t.ink) }

            item {
                FiskalCard(Modifier.fillMaxWidth()) {
                    if (vm.stavke.isEmpty()) {
                        Text(
                            "Nema stavki. Dodaj novu stavku računa s popisa iznad.",
                            Modifier.padding(FiskalSpacing.card),
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.muted,
                        )
                    } else {
                        Column {
                            vm.stavke.forEachIndexed { index, _ ->
                                StavkaRedak(vm, index, pdv)
                                if (index < vm.stavke.lastIndex) androidx.compose.material3.Divider(color = t.border, modifier = Modifier.padding(horizontal = FiskalSpacing.card))
                            }
                            androidx.compose.material3.Divider(color = t.border)
                            Column(Modifier.padding(FiskalSpacing.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (pdv) {
                                    SazetakRedak("Osnovica", hrEur(vm.zbrojNeto()), t)
                                    SazetakRedak("PDV", hrEur(vm.zbrojPdv()), t)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Način plaćanja", style = MaterialTheme.typography.titleSmall, color = t.ink)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NacinPlac.entries.forEach { np ->
                        FiskalChip(np.opis, vm.nacinPlac.value == np) { vm.nacinPlac.value = np }
                    }
                }
            }

            item {
                Polje(vm.popustRacuna.value, "Popust na račun (%)", Modifier.fillMaxWidth()) { vm.setPopustRacuna(it) }
            }

            vm.greska.value?.let { g ->
                item { Text(g, color = t.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }

        StickyFooter(vm, onNaplati)
    }
}

@Composable
private fun ArtiklTile(a: hr.obrt.fiskal.data.Artikl, modifier: Modifier, onAdd: () -> Unit) {
    val t = LocalFiskalTokens.current
    FiskalCard(modifier, onClick = onAdd) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.naziv.ifBlank { "(bez naziva)" }, style = MaterialTheme.typography.bodyLarge, color = t.ink, maxLines = 1)
                Text("${hrEur(a.jedCijena)} / ${a.jedMjere}", style = MaterialTheme.typography.bodySmall, color = t.muted)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).background(t.terracotta, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Dodaj", tint = t.terracottaInk, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** Redak potvrđene stavke — stepper za količinu, tap na redak otvara uređivanje cijene/popusta. */
@Composable
private fun StavkaRedak(vm: AppViewModel, index: Int, pdv: Boolean) {
    val t = LocalFiskalTokens.current
    val s = vm.stavke[index]
    Row(
        Modifier.fillMaxWidth().clickable { vm.zapocniUredjivanjeStavke(index) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.naziv, style = MaterialTheme.typography.bodyLarge, color = t.ink)
            Text(
                "${hrEurStr(s.jedCijena)} / ${s.jedMjere}" +
                    (if (pdv) " · PDV ${s.pdvStopa}%" else "") +
                    (s.popust.toBigDecimalOrNull()?.let { if (it.signum() != 0) " · popust ${s.popust}%" else "" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = t.muted,
            )
        }
        QuantityStepper(s.kolicina, onMinus = { vm.smanjiKolicinu(index) }, onPlus = { vm.povecajKolicinu(index) })
        Spacer(Modifier.width(10.dp))
        Text(
            hrEurStr(s.ukupno),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = t.ink,
        )
    }
}

@Composable
private fun SazetakRedak(naziv: String, vrijednost: String, t: hr.obrt.fiskal.ui.theme.FiskalTokens) {
    Row(Modifier.fillMaxWidth()) {
        Text(naziv, modifier = Modifier.weight(1f), color = t.muted, style = MaterialTheme.typography.bodySmall)
        Text(vrijednost, color = t.ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StickyFooter(vm: AppViewModel, onNaplati: () -> Unit) {
    val t = LocalFiskalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, t.border))
            .padding(horizontal = FiskalSpacing.screenX, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("UKUPNO", style = MaterialTheme.typography.labelSmall, color = t.muted)
            Text(hrEur(vm.ukupno()), style = MaterialTheme.typography.headlineSmall, color = t.ink)
        }
        FiskalTerracottaButton(
            if (vm.ucitavanje.value) "Šaljem…" else "Naplati",
            onClick = onNaplati,
            enabled = !vm.ucitavanje.value,
            trailingIcon = Icons.Rounded.ArrowForward,
        )
    }
}

/** Dijalog za dodavanje/uređivanje stavke — naziv/jed.mjere/PDV% su zaključani (iz artikla), mijenja se količina/cijena/popust. */
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
    val t = LocalFiskalTokens.current
    val imaKupca = vm.kupacNaziv.value.isNotBlank()
    FiskalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FiskalSpacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Kupac", style = MaterialTheme.typography.titleSmall, color = t.ink)
                    if (imaKupca) Text(vm.kupacNaziv.value, style = MaterialTheme.typography.bodySmall, color = t.terracotta, fontWeight = FontWeight.Bold)
                    else Text("Nije obavezno", style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
                TextButton(onClick = { naProsiri(!prosiren) }) { Text(if (prosiren) "Sakrij" else if (imaKupca) "Uredi" else "Dodaj") }
            }
            if (prosiren) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        "Krajnji kupac (upiši ručno) ili odaberi predefiniranog partnera:",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.muted,
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
private fun Polje(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = modifier,
    )
}

private fun hrEurStr(s: String): String = hrEur(s.toBigDecimalOrNull() ?: BigDecimal.ZERO)

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
            Icon(Icons.Rounded.ContentCopy, "Kopiraj $naziv")
        }
    }
}
