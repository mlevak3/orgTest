package hr.obrt.fiskal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hr.obrt.fiskal.data.Djelatnost
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

private val TAB_NASLOVI = listOf("Podaci", "Djelatnosti", "Fiskalizacija", "Printeri")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = vm.companyStore
    val company = remember { vm.editing.value ?: Tvrtka().also { vm.editing.value = it } }
    val postoji = vm.companies.any { it.id == company.id }

    // --- Tab 1: Podaci o tvrtki ---
    var naziv by remember { mutableStateOf(company.naziv) }
    var oib by remember { mutableStateOf(company.oib) }
    var pdv by remember { mutableStateOf(company.uSustavuPdv) }
    var oper by remember { mutableStateOf(company.oibOper) }
    var zadanaPdvStopa by remember { mutableStateOf(company.zadanaPdvStopa) }
    var zadaniNacinPlac by remember { mutableStateOf(company.zadaniNacinPlac) }
    var zadanaJedMjere by remember { mutableStateOf(company.zadanaJedMjere) }

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
    fun dodajDjelatnost() {
        val nova = Djelatnost(naziv = "Nova djelatnost")
        djelatnosti = (djelatnosti + nova).toMutableList()
        if (zadanaDjelatnostId.isBlank()) zadanaDjelatnostId = nova.id
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
        val azurirana = company.copy(
            naziv = naziv, oib = oib, uSustavuPdv = pdv, oibOper = oper,
            okolina = okolina, ignoreTls = ignoreTls,
            printerAddress = printerAddress,
            zadanaPdvStopa = zadanaPdvStopa, zadaniNacinPlac = zadaniNacinPlac, zadanaJedMjere = zadanaJedMjere,
            djelatnosti = djelatnosti,
            zadanaDjelatnostId = zadanaDjelatnostId,
        )
        vm.saveCompany(azurirana)
        onClose()
    }

    Scaffold(
        topBar = {
            val cs = MaterialTheme.colorScheme
            Column {
                TopAppBar(
                    title = { Text(if (postoji) "Uredi tvrtku" else "Nova tvrtka") },
                    navigationIcon = { TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Odustani") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
                )
                TabRow(selectedTabIndex = tab, containerColor = cs.primaryContainer) {
                    TAB_NASLOVI.forEachIndexed { i, naslov ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(naslov) })
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.padding(12.dp)) {
                poruka?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { spremi() }, modifier = Modifier.weight(1f)) { Text("Spremi tvrtku") }
                    if (postoji) OutlinedButton(
                        onClick = { vm.deleteCompany(company); onClose() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Obriši") }
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
                    naziv, { naziv = it }, oib, { oib = it }, oper, { oper = it }, pdv, { pdv = it },
                    zadanaPdvStopa, { zadanaPdvStopa = it }, zadaniNacinPlac, { zadaniNacinPlac = it },
                    zadanaJedMjere, { zadanaJedMjere = it },
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
                                onSuccess = { "Certifikat OK (OIB iz cert.: ${it.oibIzCertifikata ?: "?"})." },
                                onFailure = { "Certifikat/lozinka neispravni: ${it.message}" },
                            )
                        } else null
                    },
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
    oib: String, setOib: (String) -> Unit,
    oper: String, setOper: (String) -> Unit,
    pdv: Boolean, setPdv: (Boolean) -> Unit,
    zadanaPdvStopa: String, setZadanaPdvStopa: (String) -> Unit,
    zadaniNacinPlac: NacinPlac, setZadaniNacinPlac: (NacinPlac) -> Unit,
    zadanaJedMjere: String, setZadanaJedMjere: (String) -> Unit,
) {
    Text("Podaci o tvrtki", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = naziv, onValueChange = setNaziv,
        label = { Text("Naziv tvrtke / obrta") }, modifier = Modifier.fillMaxWidth(),
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
    dodajDjelatnost: () -> Unit,
    obrisiDjelatnost: (String) -> Unit,
    dodajProstor: (String) -> Unit,
    obrisiProstor: (String, String) -> Unit,
    dodajUredjaj: (String, String) -> Unit,
    obrisiUredjaj: (String, String, String) -> Unit,
) {
    Text("Djelatnosti", style = MaterialTheme.typography.titleMedium)
    Text(
        "Svaka djelatnost ima svoje poslovne prostore i naplatne uređaje. Zvjezdicom označi zadani prostor/uređaj/djelatnost.",
        style = MaterialTheme.typography.bodySmall,
    )
    djelatnosti.forEach { d ->
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = d.naziv,
                        onValueChange = { v -> updateDjelatnost(d.id) { it.copy(naziv = v) } },
                        label = { Text("Naziv djelatnosti") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { setZadanaDjelatnost(d.id) }) {
                        Icon(
                            if (zadanaDjelatnostId == d.id) Icons.Filled.Star else Icons.Filled.StarBorder,
                            "Zadana djelatnost",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (djelatnosti.size > 1) IconButton(onClick = { obrisiDjelatnost(d.id) }) {
                        Icon(Icons.Filled.Delete, "Obriši djelatnost", tint = MaterialTheme.colorScheme.error)
                    }
                }
                EnumRedak("Oznaka slijednosti", OznSlijed.entries.map { it to it.opis }, d.oznSlijed) { v ->
                    updateDjelatnost(d.id) { it.copy(oznSlijed = v) }
                }

                Divider()
                Text("Poslovni prostori", style = MaterialTheme.typography.labelLarge)
                d.poslovniProstori.forEach { p ->
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = p.oznaka,
                                onValueChange = { v -> updateProstor(d.id, p.id) { it.copy(oznaka = v) } },
                                label = { Text("Oznaka prostora") }, singleLine = true, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { updateDjelatnost(d.id) { it.copy(zadaniPoslovniProstorId = p.id) } }) {
                                Icon(
                                    if (d.zadaniPoslovniProstorId == p.id) Icons.Filled.Star else Icons.Filled.StarBorder,
                                    "Zadani prostor", tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            if (d.poslovniProstori.size > 1) IconButton(onClick = { obrisiProstor(d.id, p.id) }) {
                                Icon(Icons.Filled.Delete, "Obriši prostor", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Naplatni uređaji", style = MaterialTheme.typography.labelMedium)
                            p.naplatniUredjaji.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = u.oznaka,
                                        onValueChange = { v -> updateUredjaj(d.id, p.id, u.id) { it.copy(oznaka = v) } },
                                        label = { Text("Oznaka uređaja") }, singleLine = true, modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { updateDjelatnost(d.id) { it.copy(zadaniNaplatniUredjajId = u.id) } }) {
                                        Icon(
                                            if (d.zadaniNaplatniUredjajId == u.id) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            "Zadani uređaj", tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                    if (p.naplatniUredjaji.size > 1) IconButton(onClick = { obrisiUredjaj(d.id, p.id, u.id) }) {
                                        Icon(Icons.Filled.Delete, "Obriši uređaj", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            TextButton(onClick = { dodajUredjaj(d.id, p.id) }) {
                                Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dodaj uređaj")
                            }
                        }
                    }
                }
                TextButton(onClick = { dodajProstor(d.id) }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dodaj poslovni prostor")
                }
            }
        }
    }
    OutlinedButton(onClick = dodajDjelatnost, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Dodaj djelatnost")
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
) {
    Text("FINA certifikat (.p12 / .pfx)", style = MaterialTheme.typography.titleMedium)
    Text(certInfo, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

private fun certStatus(postoji: Boolean) =
    if (postoji) "Status: certifikat učitan." else "Status: certifikat NIJE učitan."

private fun caStatus(postoji: Boolean) =
    if (postoji) "Status: FINA CA učitan." else "Status: koristi se ugrađeni FINA CA."

@Composable
private fun <T> EnumRedak(naslov: String, opcije: List<Pair<T, String>>, odabrano: T, naOdabir: (T) -> Unit) {
    Column {
        Text(naslov, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opcije.forEach { (vrijednost, opis) ->
                FilterChip(selected = odabrano == vrijednost, onClick = { naOdabir(vrijednost) }, label = { Text(opis) })
            }
        }
    }
}
