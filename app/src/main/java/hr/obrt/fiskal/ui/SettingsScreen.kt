@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hr.obrt.fiskal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hr.obrt.fiskal.data.Djelatnost
import hr.obrt.fiskal.ui.components.FiskalInput
import hr.obrt.fiskal.data.NaplatniUredaj
import hr.obrt.fiskal.data.PoslovniProstor
import hr.obrt.fiskal.data.Tvrtka
import hr.obrt.fiskal.fiskal.BluetoothPrinter
import hr.obrt.fiskal.fiskal.CaStore
import hr.obrt.fiskal.fiskal.EscPosReceiptBuilder
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalClient
import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.fiskal.TlsTrust
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TAB_NASLOVI = listOf("Podaci", "Djelatnosti", "Fiskalizacija", "Printeri", "Sig. kopija")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onClose: () -> Unit, onOpenFiskalLog: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = vm.companyStore
    val company = remember { vm.editing.value ?: Tvrtka().also { vm.editing.value = it } }
    val postoji = vm.companies.any { it.id == company.id }

    // --- Tab 1: Podaci o tvrtki ---
    var naziv by remember { mutableStateOf(company.naziv) }
    var adresa by remember { mutableStateOf(company.adresa) }
    var oib by remember { mutableStateOf(company.oib) }
    var pdv by remember { mutableStateOf(company.uSustavuPdv) }
    var oper by remember { mutableStateOf(company.oibOper) }
    var zadanaPdvStopa by remember { mutableStateOf(company.zadanaPdvStopa) }
    var zadaniNacinPlac by remember { mutableStateOf(company.zadaniNacinPlac) }
    var zadanaJedMjere by remember { mutableStateOf(company.zadanaJedMjere) }
    var logoPostoji by remember { mutableStateOf(store.logoPostoji(company.id)) }

    // --- Tab 2: Djelatnosti (duboka kopija radi neovisnog uređivanja prije spremanja) ---
    var djelatnosti by remember { mutableStateOf(dubokaKopija(company.djelatnosti)) }
    var zadanaDjelatnostId by remember { mutableStateOf(company.zadanaDjelatnostId) }

    // --- Tab 3: Fiskalizacija ---
    var okolina by remember { mutableStateOf(company.okolina) }
    var ignoreTls by remember { mutableStateOf(company.ignoreTls) }
    var lozinka by remember { mutableStateOf(store.lozinka(company.id)) }
    var certInfo by remember { mutableStateOf(certStatus(store.certPostoji(company.id))) }
    var caInfo by remember { mutableStateOf(caStatus(store.caPostoji(company.id))) }

    // --- Tab 4: Printeri ---
    var printerAddress by remember { mutableStateOf(company.printerAddress) }
    var printerName by remember { mutableStateOf<String?>(null) }
    var pokaziBirac by remember { mutableStateOf(false) }

    var poruka by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }

    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pokaziBirac = true else poruka = "Bluetooth dozvola nije odobrena."
    }
    fun otvoriBirac() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            pokaziBirac = true
        }
    }
    LaunchedEffect(printerAddress) {
        if (printerAddress.isNotBlank()) {
            printerName = BluetoothPrinter.uparenaUredaji(ctx).firstOrNull { it.address == printerAddress }?.name
        }
    }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) { "Datoteka nije valjana slika." }
            store.spremiLogo(company.id, bytes)
            logoPostoji = true
            poruka = "Logo učitan."
        }.onFailure { poruka = "Greška pri učitavanju loga: ${it.message}" }
    }
    val certPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            store.spremiCert(company.id, bytes)
            certInfo = certStatus(true)
            poruka = "Certifikat učitan (${bytes.size} B). Unesi lozinku i spremi."
        }.onFailure { poruka = "Greška pri učitavanju: ${it.message}" }
    }
    val caPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val certs = TlsTrust.parseCertificates(bytes.inputStream())
            require(certs.isNotEmpty()) { "Datoteka ne sadrži X.509 certifikate." }
            store.spremiCa(company.id, bytes)
            caInfo = "Status: učitano (${certs.size} certifikat/a)."
            poruka = "FINA CA učitan."
        }.onFailure { poruka = "Greška pri učitavanju CA: ${it.message}" }
    }

    // --- Funkcije za uređivanje djelatnosti/prostora/uređaja (nova kopija → recompozicija) ---
    fun updateDjelatnost(id: String, transform: (Djelatnost) -> Djelatnost) {
        djelatnosti = djelatnosti.map { if (it.id == id) transform(it) else it }.toMutableList()
    }
    fun updateProstor(dId: String, pId: String, transform: (PoslovniProstor) -> PoslovniProstor) {
        updateDjelatnost(dId) { d ->
            d.copy(poslovniProstori = d.poslovniProstori.map { if (it.id == pId) transform(it) else it }.toMutableList())
        }
    }
    fun updateUredjaj(dId: String, pId: String, uId: String, transform: (NaplatniUredaj) -> NaplatniUredaj) {
        updateProstor(dId, pId) { p ->
            p.copy(naplatniUredjaji = p.naplatniUredjaji.map { if (it.id == uId) transform(it) else it }.toMutableList())
        }
    }
    fun dodajDjelatnost(): String {
        val nova = Djelatnost(naziv = "Nova djelatnost")
        djelatnosti = (djelatnosti + nova).toMutableList()
        if (zadanaDjelatnostId.isBlank()) zadanaDjelatnostId = nova.id
        return nova.id
    }
    fun obrisiDjelatnost(id: String) {
        if (djelatnosti.size <= 1) return
        djelatnosti = djelatnosti.filterNot { it.id == id }.toMutableList()
        if (zadanaDjelatnostId == id) zadanaDjelatnostId = djelatnosti.first().id
    }
    fun dodajProstor(dId: String) {
        updateDjelatnost(dId) { d ->
            val novi = PoslovniProstor(oznaka = "POSL${d.poslovniProstori.size + 1}")
            d.copy(poslovniProstori = (d.poslovniProstori + novi).toMutableList())
        }
    }
    fun obrisiProstor(dId: String, pId: String) {
        updateDjelatnost(dId) { d ->
            if (d.poslovniProstori.size <= 1) return@updateDjelatnost d
            val ostali = d.poslovniProstori.filterNot { it.id == pId }.toMutableList()
            val noviZadani = if (d.zadaniPoslovniProstorId == pId) ostali.first().id else d.zadaniPoslovniProstorId
            d.copy(poslovniProstori = ostali, zadaniPoslovniProstorId = noviZadani)
        }
    }
    fun dodajUredjaj(dId: String, pId: String) {
        updateProstor(dId, pId) { p ->
            val novi = NaplatniUredaj(oznaka = "${p.naplatniUredjaji.size + 1}")
            p.copy(naplatniUredjaji = (p.naplatniUredjaji + novi).toMutableList())
        }
    }
    fun obrisiUredjaj(dId: String, pId: String, uId: String) {
        updateProstor(dId, pId) { p ->
            if (p.naplatniUredjaji.size <= 1) return@updateProstor p
            p.copy(naplatniUredjaji = p.naplatniUredjaji.filterNot { it.id == uId }.toMutableList())
        }
    }

    fun spremi() {
        store.postaviLozinku(company.id, lozinka)
        // Sljedeći brojevi računa (po prostoru/uređaju) mijenjaju se isključivo kroz
        // fiskalizaciju, ne kroz ovaj ekran — uvijek preuzmi najsvježije spremljene
        // vrijednosti kako spremanje ovdje (npr. samo naziva ili printera) ne bi
        // slučajno vratilo brojač unatrag na zastarjelu vrijednost uhvaćenu pri
        // otvaranju ekrana.
        val spremljenaTvrtka = store.sve().firstOrNull { it.id == company.id }
        val azurirana = company.copy(
            naziv = naziv, adresa = adresa, oib = oib, uSustavuPdv = pdv, oibOper = oper,
            okolina = okolina, ignoreTls = ignoreTls,
            printerAddress = printerAddress,
            zadanaPdvStopa = zadanaPdvStopa, zadaniNacinPlac = zadaniNacinPlac, zadanaJedMjere = zadanaJedMjere,
            djelatnosti = spojiSljedeceBrojeve(djelatnosti, spremljenaTvrtka?.djelatnosti),
            zadanaDjelatnostId = zadanaDjelatnostId,
        )
        vm.saveCompany(azurirana)
        onClose()
    }

    val fiskalTokens = hr.obrt.fiskal.ui.theme.LocalFiskalTokens.current
    // Poruke (echo test, certifikat, logo…) prikazuju se kao snackbar — uočljivo,
    // umjesto sitnog teksta pri dnu ekrana.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(poruka) {
        poruka?.let {
            snackbarHostState.showSnackbar(it)
            poruka = null
        }
    }
    Scaffold(
        containerColor = fiskalTokens.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                hr.obrt.fiskal.ui.components.LightHeader(
                    if (postoji) "Uredi tvrtku" else "Nova tvrtka",
                    onBack = onClose,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = hr.obrt.fiskal.ui.theme.FiskalSpacing.screenX, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TAB_NASLOVI.forEachIndexed { i, naslov ->
                        hr.obrt.fiskal.ui.components.FiskalChip(naslov, tab == i) { tab = i }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.padding(hr.obrt.fiskal.ui.theme.FiskalSpacing.screenX)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    hr.obrt.fiskal.ui.components.FiskalPrimaryButton("Spremi tvrtku", onClick = { spremi() }, modifier = Modifier.weight(1f))
                    if (postoji) hr.obrt.fiskal.ui.components.FiskalOutlineButton(
                        "Obriši",
                        onClick = { vm.deleteCompany(company); onClose() },
                        destruktivno = true,
                    )
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> TabPodaci(
                    naziv, { naziv = it }, adresa, { adresa = it }, oib, { oib = it }, oper, { oper = it }, pdv, { pdv = it },
                    zadanaPdvStopa, { zadanaPdvStopa = it }, zadaniNacinPlac, { zadaniNacinPlac = it },
                    zadanaJedMjere, { zadanaJedMjere = it },
                    logoPostoji,
                    onUcitajLogo = { logoPicker.launch(arrayOf("image/*")) },
                    onUkloniLogo = { store.obrisiLogo(company.id); logoPostoji = false },
                )
                1 -> TabDjelatnosti(
                    djelatnosti, zadanaDjelatnostId, { zadanaDjelatnostId = it },
                    ::updateDjelatnost, ::updateProstor, ::updateUredjaj,
                    ::dodajDjelatnost, ::obrisiDjelatnost, ::dodajProstor, ::obrisiProstor,
                    ::dodajUredjaj, ::obrisiUredjaj,
                )
                2 -> TabFiskalizacija(
                    okolina, { okolina = it }, ignoreTls, { ignoreTls = it },
                    certInfo, caInfo, lozinka, { lozinka = it },
                    onUcitajCert = { certPicker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")) },
                    onUkloniCert = { store.obrisiCert(company.id); certInfo = certStatus(false); lozinka = "" },
                    certPostoji = store.certPostoji(company.id),
                    onUcitajCa = { caPicker.launch(arrayOf("*/*")) },
                    onUkloniCa = { store.obrisiCa(company.id); caInfo = caStatus(false) },
                    caPostoji = store.caPostoji(company.id),
                    onTestEcho = {
                        val okol = okolina; val tls = ignoreTls
                        poruka = "Testiram vezu…"
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                val ca = CaStore.loadExtraCas(ctx, store.caBytes(company.id))
                                FiskalClient(okol, tls, ca).echo("test")
                            }
                            poruka = res.fold(
                                onSuccess = { "Veza OK (${okol.opis}). Odgovor: \"$it\"" },
                                onFailure = { "Test veze nije uspio: ${it.message}" },
                            )
                        }
                    },
                    onProvjeriCert = {
                        store.postaviLozinku(company.id, lozinka)
                        poruka = if (store.certPostoji(company.id) && lozinka.isNotBlank()) {
                            runCatching {
                                FiskalCertificate.load(store.certBytes(company.id)!!.inputStream(), lozinka.toCharArray())
                            }.fold(
                                onSuccess = { c ->
                                    val datum = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.ROOT).format(c.vrijediDo)
                                    val upozorenje = if (!c.jeValjan) " ⚠ CERTIFIKAT JE ISTEKAO/NEVALJAN — CIS će ga odbiti." else ""
                                    val okolinaUpozorenje = if (okolina == FiskalOkolina.TEST && !c.izdavatelj.contains("DEMO", ignoreCase = true))
                                        " ⚠ Izdavatelj ne sadrži 'DEMO' — provjeri koristiš li stvarno FINA DEMO certifikat za testnu okolinu (produkcijski certifikat CIS test odbija s greškom potpisa)."
                                    else ""
                                    "Certifikat OK. OIB: ${c.oibIzCertifikata ?: "?"} · Izdavatelj: ${c.izdavatelj} · Vrijedi do: $datum.$upozorenje$okolinaUpozorenje"
                                },
                                onFailure = { "Certifikat/lozinka neispravni: ${it.message}" },
                            )
                        } else null
                    },
                    onOpenFiskalLog = { vm.loadFiskalLog(); onOpenFiskalLog() },
                )
                3 -> TabPrinteri(
                    printerAddress, printerName,
                    onOdaberi = { otvoriBirac() },
                    onUkloni = { printerAddress = ""; printerName = null },
                    onTest = {
                        poruka = "Šaljem testni ispis…"
                        scope.launch {
                            val bytes = EscPosReceiptBuilder.buildTestPage(naziv.ifBlank { "Fiskal Obrt" })
                            val res = withContext(Dispatchers.IO) { BluetoothPrinter.posalji(ctx, printerAddress, bytes) }
                            poruka = res.fold({ "Testni ispis poslan." }, { "Ispis nije uspio: ${it.message}" })
                        }
                    },
                )
                4 -> TabSigKopija(vm) { poruka = it }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pokaziBirac) {
        val uredaji = remember { BluetoothPrinter.uparenaUredaji(ctx) }
        AlertDialog(
            onDismissRequest = { pokaziBirac = false },
            title = { Text("Odaberi Bluetooth pisač") },
            text = {
                if (uredaji.isEmpty()) {
                    Text("Nema uparenih Bluetooth uređaja. Upari pisač prvo u Android postavkama (Bluetooth).")
                } else {
                    Column {
                        uredaji.forEach { d ->
                            TextButton(onClick = {
                                printerAddress = d.address; printerName = d.name; pokaziBirac = false
                            }) { Text("${d.name} (${d.address})") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pokaziBirac = false }) { Text("Zatvori") } },
        )
    }
}

@Composable
private fun TabPodaci(
    naziv: String, setNaziv: (String) -> Unit,
    adresa: String, setAdresa: (String) -> Unit,
    oib: String, setOib: (String) -> Unit,
    oper: String, setOper: (String) -> Unit,
    pdv: Boolean, setPdv: (Boolean) -> Unit,
    zadanaPdvStopa: String, setZadanaPdvStopa: (String) -> Unit,
    zadaniNacinPlac: NacinPlac, setZadaniNacinPlac: (NacinPlac) -> Unit,
    zadanaJedMjere: String, setZadanaJedMjere: (String) -> Unit,
    logoPostoji: Boolean,
    onUcitajLogo: () -> Unit, onUkloniLogo: () -> Unit,
) {
    Text("Podaci o tvrtki", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = naziv, onValueChange = setNaziv,
        label = { Text("Naziv tvrtke / obrta") }, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = adresa, onValueChange = setAdresa,
        label = { Text("Adresa tvrtke") },
        placeholder = { Text("Ulica i broj, poštanski broj mjesto") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = oib, onValueChange = { setOib(it.filter(Char::isDigit).take(11)) },
        label = { Text("OIB obveznika (11 znamenki)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = oper, onValueChange = { setOper(it.filter(Char::isDigit).take(11)) },
        label = { Text("OIB operatera (ako je različit)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = pdv, onCheckedChange = setPdv)
        Spacer(Modifier.width(8.dp)); Text("Obveznik u sustavu PDV-a")
    }

    Divider()
    Text("Logo tvrtke", style = MaterialTheme.typography.titleMedium)
    Text(
        if (logoPostoji) "Logo je učitan — prikazuje se na ispisu, PDF-u i emailu."
        else "Nema loga (naslov računa ostaje samo tekstualni).",
        style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUcitajLogo) { Text("Učitaj logo") }
        if (logoPostoji) OutlinedButton(onClick = onUkloniLogo) { Text("Ukloni") }
    }

    Divider()
    Text("Zadane vrijednosti", style = MaterialTheme.typography.titleMedium)
    Text("Ubrzavaju unos novog računa i artikla.", style = MaterialTheme.typography.bodySmall)
    if (pdv) {
        OutlinedTextField(
            value = zadanaPdvStopa, onValueChange = setZadanaPdvStopa,
            label = { Text("Zadana PDV stopa (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = zadanaJedMjere, onValueChange = setZadanaJedMjere,
        label = { Text("Zadana jedinica mjere") }, modifier = Modifier.fillMaxWidth(),
    )
    EnumRedak("Zadani način plaćanja", NacinPlac.entries.map { it to it.oznaka }, zadaniNacinPlac, setZadaniNacinPlac)
}

@Composable
private fun TabDjelatnosti(
    djelatnosti: List<Djelatnost>,
    zadanaDjelatnostId: String,
    setZadanaDjelatnost: (String) -> Unit,
    updateDjelatnost: (String, (Djelatnost) -> Djelatnost) -> Unit,
    updateProstor: (String, String, (PoslovniProstor) -> PoslovniProstor) -> Unit,
    updateUredjaj: (String, String, String, (NaplatniUredaj) -> NaplatniUredaj) -> Unit,
    dodajDjelatnost: () -> String,
    obrisiDjelatnost: (String) -> Unit,
    dodajProstor: (String) -> Unit,
    obrisiProstor: (String, String) -> Unit,
    dodajUredjaj: (String, String) -> Unit,
    obrisiUredjaj: (String, String, String) -> Unit,
) {
    var prosirenId by remember { mutableStateOf<String?>(djelatnosti.singleOrNull()?.id) }

    Text("Djelatnosti", style = MaterialTheme.typography.titleMedium)
    Text(
        "Dodirni djelatnost za uređivanje poslovnog prostora i naplatnog uređaja. Zvjezdicom označi zadani prostor/uređaj/djelatnost.",
        style = MaterialTheme.typography.bodySmall,
    )
    djelatnosti.forEach { d ->
        val prosiren = prosirenId == d.id
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { prosirenId = if (prosiren) null else d.id },
                ) {
                    Icon(
                        if (prosiren) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        "Prikaži/sakrij", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        d.naziv.ifBlank { "Djelatnost" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { setZadanaDjelatnost(d.id) }) {
                        Icon(
                            if (zadanaDjelatnostId == d.id) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "Zadana djelatnost",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (djelatnosti.size > 1) IconButton(onClick = {
                        obrisiDjelatnost(d.id)
                        if (prosirenId == d.id) prosirenId = null
                    }) {
                        Icon(Icons.Filled.Delete, "Obriši djelatnost", tint = MaterialTheme.colorScheme.error)
                    }
                }

                AnimatedVisibility(
                    visible = prosiren,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FiskalInput(
                            d.naziv,
                            { v -> updateDjelatnost(d.id) { it.copy(naziv = v) } },
                            "Naziv djelatnosti",
                            Modifier.fillMaxWidth(),
                        )
                        EnumRedak("Oznaka slijednosti", OznSlijed.entries.map { it to it.opis }, d.oznSlijed) { v ->
                            updateDjelatnost(d.id) { it.copy(oznSlijed = v) }
                        }

                        Divider()
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Poslovni prostori", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { dodajProstor(d.id) }) {
                                Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("Dodaj prostor")
                            }
                        }
                        d.poslovniProstori.forEach { p ->
                            ProstorBlok(
                                d, p,
                                updateDjelatnost = updateDjelatnost,
                                updateProstor = updateProstor,
                                updateUredjaj = updateUredjaj,
                                obrisiProstor = obrisiProstor,
                                dodajUredjaj = dodajUredjaj,
                                obrisiUredjaj = obrisiUredjaj,
                            )
                        }
                    }
                }
            }
        }
    }
    OutlinedButton(
        onClick = { prosirenId = dodajDjelatnost() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dodaj djelatnost")
    }
}

/**
 * Jedan poslovni prostor kao vizualno odvojen (udubljen) blok: oznaka prostora
 * na vrhu, ispod njegovi naplatni uređaji — jasna hijerarhija umjesto niza
 * jednakih polja.
 */
@Composable
private fun ProstorBlok(
    d: Djelatnost,
    p: PoslovniProstor,
    updateDjelatnost: (String, (Djelatnost) -> Djelatnost) -> Unit,
    updateProstor: (String, String, (PoslovniProstor) -> PoslovniProstor) -> Unit,
    updateUredjaj: (String, String, String, (NaplatniUredaj) -> NaplatniUredaj) -> Unit,
    obrisiProstor: (String, String) -> Unit,
    dodajUredjaj: (String, String) -> Unit,
    obrisiUredjaj: (String, String, String) -> Unit,
) {
    val t = hr.obrt.fiskal.ui.theme.LocalFiskalTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.surfaceSunken, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FiskalInput(
                p.oznaka,
                { v -> updateProstor(d.id, p.id) { it.copy(oznaka = v) } },
                "Oznaka prostora",
                Modifier.weight(1f),
            )
            ZvjezdicaGumb(d.zadaniPoslovniProstorId == p.id, "Zadani prostor") {
                updateDjelatnost(d.id) { it.copy(zadaniPoslovniProstorId = p.id) }
            }
            if (d.poslovniProstori.size > 1) BrisanjeGumb("Obriši prostor") { obrisiProstor(d.id, p.id) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
            Text("Naplatni uređaji", style = MaterialTheme.typography.labelMedium, color = t.muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { dodajUredjaj(d.id, p.id) }) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(2.dp)); Text("Dodaj uređaj")
            }
        }
        p.naplatniUredjaji.forEach { u ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                FiskalInput(
                    u.oznaka,
                    { v -> updateUredjaj(d.id, p.id, u.id) { it.copy(oznaka = v) } },
                    "Oznaka uređaja",
                    Modifier.weight(1f),
                )
                ZvjezdicaGumb(d.zadaniNaplatniUredjajId == u.id, "Zadani uređaj") {
                    updateDjelatnost(d.id) { it.copy(zadaniNaplatniUredjajId = u.id) }
                }
                if (p.naplatniUredjaji.size > 1) BrisanjeGumb("Obriši uređaj") { obrisiUredjaj(d.id, p.id, u.id) }
            }
        }
    }
}

@Composable
private fun ZvjezdicaGumb(zadano: Boolean, opis: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (zadano) Icons.Filled.Star else Icons.Filled.StarBorder,
            opis,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BrisanjeGumb(opis: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Delete, opis, tint = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TabFiskalizacija(
    okolina: FiskalOkolina, setOkolina: (FiskalOkolina) -> Unit,
    ignoreTls: Boolean, setIgnoreTls: (Boolean) -> Unit,
    certInfo: String, caInfo: String,
    lozinka: String, setLozinka: (String) -> Unit,
    onUcitajCert: () -> Unit, onUkloniCert: () -> Unit, certPostoji: Boolean,
    onUcitajCa: () -> Unit, onUkloniCa: () -> Unit, caPostoji: Boolean,
    onTestEcho: () -> Unit, onProvjeriCert: () -> Unit,
    onOpenFiskalLog: () -> Unit,
) {
    Text("FINA certifikat (.p12 / .pfx)", style = MaterialTheme.typography.titleMedium)
    Text(certInfo, style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUcitajCert) { Text("Učitaj certifikat") }
        if (certPostoji) OutlinedButton(onClick = onUkloniCert) { Text("Ukloni") }
    }
    OutlinedTextField(
        value = lozinka, onValueChange = setLozinka,
        label = { Text("Lozinka certifikata") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onProvjeriCert, modifier = Modifier.fillMaxWidth()) { Text("Provjeri certifikat") }

    Divider()
    Text("FINA CA (TLS — za PRODUKCIJU)", style = MaterialTheme.typography.titleMedium)
    Text(caInfo, style = MaterialTheme.typography.bodySmall)
    Text(
        "Fina Root CA + Fina RDC 2020 su već ugrađeni; uvezi samo ako FINA promijeni lanac.",
        style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUcitajCa) { Text("Učitaj CA") }
        if (caPostoji) OutlinedButton(onClick = onUkloniCa) { Text("Ukloni") }
    }

    Divider()
    Text("Okolina", style = MaterialTheme.typography.titleMedium)
    EnumRedak("Okolina", FiskalOkolina.entries.map { it to it.opis }, okolina, setOkolina)
    if (okolina == FiskalOkolina.TEST) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = ignoreTls, onCheckedChange = setIgnoreTls)
            Spacer(Modifier.width(8.dp)); Text("Zanemari TLS provjeru (samo TEST)")
        }
    }
    OutlinedButton(onClick = onTestEcho, modifier = Modifier.fillMaxWidth()) { Text("Test veze (Echo)") }

    Divider()
    Text("Log fiskalizacije", style = MaterialTheme.typography.titleMedium)
    Text(
        "Zahtjev i odgovor CIS-a za svaki pokušaj fiskalizacije — korisno za dijagnostiku grešaka.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = onOpenFiskalLog, modifier = Modifier.fillMaxWidth()) { Text("Prikaži log fiskalizacije") }
}

/** Izvoz/uvoz svih podataka aplikacije — kao tab u postavkama, ne zaseban ekran. */
@Composable
private fun TabSigKopija(vm: AppViewModel, onPoruka: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = hr.obrt.fiskal.ui.theme.LocalFiskalTokens.current
    var radi by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        radi = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = hr.obrt.fiskal.data.BackupManager.export(ctx)
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Ne mogu otvoriti datoteku za pisanje.")
                }
            }
            radi = false
            onPoruka(ok.fold({ "Sigurnosna kopija spremljena." }, { "Izvoz nije uspio: ${it.message}" }))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        radi = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val json = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                    hr.obrt.fiskal.data.BackupManager.import(ctx, json).getOrThrow()
                }
            }
            radi = false
            res.onSuccess { vm.refreshCompanies() }
            onPoruka(res.fold({ n -> "Uvezeno tvrtki: $n." }, { "Uvoz nije uspio: ${it.message}" }))
        }
    }

    Text("Sigurnosna kopija", style = MaterialTheme.typography.titleMedium)
    Text(
        "Izvezi ili uvezi sve podatke aplikacije: tvrtke, FINA certifikate, šifrarnik artikala i povijest računa.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Box(Modifier.fillMaxWidth().background(t.errorBg, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(
            "Datoteka sadrži osjetljive podatke — privatni certifikat i njegovu lozinku. Čuvaj je sigurno i ne dijeli s nepoznatima.",
            style = MaterialTheme.typography.bodySmall,
            color = t.error,
        )
    }
    hr.obrt.fiskal.ui.components.FiskalPrimaryButton(
        "Izvezi sigurnosnu kopiju",
        enabled = !radi,
        onClick = {
            val ime = "fiskal-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())}.json"
            exportLauncher.launch(ime)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    hr.obrt.fiskal.ui.components.FiskalOutlineButton(
        "Uvezi sigurnosnu kopiju",
        enabled = !radi,
        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (radi) LinearProgressIndicator(Modifier.fillMaxWidth())
}

@Composable
private fun TabPrinteri(
    printerAddress: String, printerName: String?,
    onOdaberi: () -> Unit, onUkloni: () -> Unit, onTest: () -> Unit,
) {
    Text("POS pisač (Bluetooth)", style = MaterialTheme.typography.titleMedium)
    Text(
        if (printerAddress.isBlank()) "Nije odabran pisač."
        else "Odabran: ${printerName ?: printerAddress}",
        style = MaterialTheme.typography.bodySmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOdaberi) { Text("Odaberi pisač") }
        if (printerAddress.isNotBlank()) {
            OutlinedButton(onClick = onTest) { Text("Testni ispis") }
            OutlinedButton(onClick = onUkloni) { Text("Ukloni") }
        }
    }
}

private fun dubokaKopija(list: List<Djelatnost>): MutableList<Djelatnost> = list.map { d ->
    d.copy(poslovniProstori = d.poslovniProstori.map { p ->
        p.copy(naplatniUredjaji = p.naplatniUredjaji.map { it.copy() }.toMutableList())
    }.toMutableList())
}.toMutableList()

/** Preuzima sljedeciBroj (po prostoru/uređaju) iz trenutno spremljenog stanja u uređenu listu. */
private fun spojiSljedeceBrojeve(uredjene: List<Djelatnost>, spremljene: List<Djelatnost>?): MutableList<Djelatnost> {
    if (spremljene == null) return uredjene.toMutableList()
    return uredjene.map { d ->
        val spD = spremljene.firstOrNull { it.id == d.id }
        if (spD == null) d else d.copy(
            poslovniProstori = d.poslovniProstori.map { p ->
                val spP = spD.poslovniProstori.firstOrNull { it.id == p.id }
                if (spP == null) p else p.copy(
                    sljedeciBroj = spP.sljedeciBroj,
                    naplatniUredjaji = p.naplatniUredjaji.map { u ->
                        val spU = spP.naplatniUredjaji.firstOrNull { it.id == u.id }
                        if (spU == null) u else u.copy(sljedeciBroj = spU.sljedeciBroj)
                    }.toMutableList(),
                )
            }.toMutableList(),
        )
    }.toMutableList()
}

private fun certStatus(postoji: Boolean) =
    if (postoji) "Status: certifikat učitan." else "Status: certifikat NIJE učitan."

private fun caStatus(postoji: Boolean) =
    if (postoji) "Status: FINA CA učitan." else "Status: koristi se ugrađeni FINA CA."

@Composable
private fun <T> EnumRedak(naslov: String, opcije: List<Pair<T, String>>, odabrano: T, naOdabir: (T) -> Unit) {
    Column {
        Text(naslov, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            opcije.forEach { (vrijednost, opis) ->
                FilterChip(selected = odabrano == vrijednost, onClick = { naOdabir(vrijednost) }, label = { Text(opis) })
            }
        }
    }
}
