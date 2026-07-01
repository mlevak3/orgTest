package hr.obrt.fiskal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Tvrtka
import hr.obrt.fiskal.fiskal.CaStore
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalClient
import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.fiskal.TlsTrust
import hr.obrt.fiskal.model.OznSlijed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = vm.companyStore
    val company = remember { vm.editing.value ?: Tvrtka().also { vm.editing.value = it } }
    val postoji = vm.companies.any { it.id == company.id }

    var naziv by remember { mutableStateOf(company.naziv) }
    var oib by remember { mutableStateOf(company.oib) }
    var pdv by remember { mutableStateOf(company.uSustavuPdv) }
    var posPr by remember { mutableStateOf(company.oznPosPr) }
    var napUr by remember { mutableStateOf(company.oznNapUr) }
    var slijed by remember { mutableStateOf(company.oznSlijed) }
    var oper by remember { mutableStateOf(company.oibOper) }
    var okolina by remember { mutableStateOf(company.okolina) }
    var ignoreTls by remember { mutableStateOf(company.ignoreTls) }
    var lozinka by remember { mutableStateOf(store.lozinka(company.id)) }
    var broj by remember { mutableStateOf(company.sljedeciBroj.toString()) }
    var certInfo by remember { mutableStateOf(certStatus(store.certPostoji(company.id))) }
    var caInfo by remember { mutableStateOf(caStatus(store.caPostoji(company.id))) }
    var poruka by remember { mutableStateOf<String?>(null) }

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

    fun spremi() {
        store.postaviLozinku(company.id, lozinka)
        val azurirana = company.copy(
            naziv = naziv, oib = oib, uSustavuPdv = pdv, oznPosPr = posPr, oznNapUr = napUr,
            oznSlijed = slijed, oibOper = oper, okolina = okolina, ignoreTls = ignoreTls,
            sljedeciBroj = broj.toLongOrNull() ?: 1L,
        )
        vm.saveCompany(azurirana)
        onClose()
    }

    Scaffold(
        topBar = {
            val cs = MaterialTheme.colorScheme
            TopAppBar(
                title = { Text(if (postoji) "Uredi tvrtku" else "Nova tvrtka") },
                navigationIcon = { TextButton(onClick = onClose, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Odustani") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = naziv, onValueChange = { naziv = it },
                label = { Text("Naziv tvrtke / obrta") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = oib, onValueChange = { oib = it.filter(Char::isDigit).take(11) },
                label = { Text("OIB obveznika (11 znamenki)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = oper, onValueChange = { oper = it.filter(Char::isDigit).take(11) },
                label = { Text("OIB operatera (ako je različit)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = pdv, onCheckedChange = { pdv = it })
                Spacer(Modifier.width(8.dp)); Text("Obveznik u sustavu PDV-a")
            }

            Divider()
            Text("Poslovni prostor", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = posPr, onValueChange = { posPr = it },
                label = { Text("Oznaka poslovnog prostora (OznPosPr)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = napUr, onValueChange = { napUr = it },
                label = { Text("Oznaka naplatnog uređaja (OznNapUr)") }, modifier = Modifier.fillMaxWidth(),
            )
            EnumRedak("Oznaka slijednosti", OznSlijed.entries.map { it to it.opis }, slijed) { slijed = it }

            Divider()
            Text("FINA certifikat (.p12 / .pfx)", style = MaterialTheme.typography.titleMedium)
            Text(certInfo, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { certPicker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*")) }) {
                    Text("Učitaj certifikat")
                }
                if (store.certPostoji(company.id)) OutlinedButton(onClick = {
                    store.obrisiCert(company.id); certInfo = certStatus(false); lozinka = ""
                }) { Text("Ukloni") }
            }
            OutlinedTextField(
                value = lozinka, onValueChange = { lozinka = it },
                label = { Text("Lozinka certifikata") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Divider()
            Text("FINA CA (TLS — za PRODUKCIJU)", style = MaterialTheme.typography.titleMedium)
            Text(caInfo, style = MaterialTheme.typography.bodySmall)
            Text(
                "Fina Root CA + Fina RDC 2020 su već ugrađeni; uvezi samo ako FINA promijeni lanac.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { caPicker.launch(arrayOf("*/*")) }) { Text("Učitaj CA") }
                if (store.caPostoji(company.id)) OutlinedButton(onClick = {
                    store.obrisiCa(company.id); caInfo = caStatus(false)
                }) { Text("Ukloni") }
            }

            Divider()
            Text("Okolina i numeracija", style = MaterialTheme.typography.titleMedium)
            EnumRedak("Okolina", FiskalOkolina.entries.map { it to it.opis }, okolina) { okolina = it }
            if (okolina == FiskalOkolina.TEST) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = ignoreTls, onCheckedChange = { ignoreTls = it })
                    Spacer(Modifier.width(8.dp)); Text("Zanemari TLS provjeru (samo TEST)")
                }
            }
            OutlinedButton(
                onClick = {
                    val okol = okolina
                    val tls = ignoreTls
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test veze (Echo)") }
            OutlinedTextField(
                value = broj, onValueChange = { broj = it.filter(Char::isDigit) },
                label = { Text("Sljedeći broj računa (BrOznRac)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            poruka?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    store.postaviLozinku(company.id, lozinka)
                    val info = if (store.certPostoji(company.id) && lozinka.isNotBlank()) {
                        runCatching {
                            FiskalCertificate.load(store.certBytes(company.id)!!.inputStream(), lozinka.toCharArray())
                        }.fold(
                            onSuccess = { "Certifikat OK (OIB iz cert.: ${it.oibIzCertifikata ?: "?"})." },
                            onFailure = { "Certifikat/lozinka neispravni: ${it.message}" },
                        )
                    } else null
                    poruka = info
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Provjeri certifikat") }

            Button(onClick = { spremi() }, modifier = Modifier.fillMaxWidth()) { Text("Spremi tvrtku") }

            if (postoji) {
                OutlinedButton(
                    onClick = { vm.deleteCompany(company); onClose() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Obriši tvrtku") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
