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
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.model.OznSlijed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = vm.repo

    var oib by remember { mutableStateOf(repo.oib) }
    var pdv by remember { mutableStateOf(repo.uSustavuPdv) }
    var posPr by remember { mutableStateOf(repo.oznPosPr) }
    var napUr by remember { mutableStateOf(repo.oznNapUr) }
    var slijed by remember { mutableStateOf(repo.oznSlijed) }
    var oper by remember { mutableStateOf(repo.oibOper) }
    var okolina by remember { mutableStateOf(repo.okolina) }
    var ignoreTls by remember { mutableStateOf(repo.ignoreTlsTrust) }
    var lozinka by remember { mutableStateOf(repo.certPassword) }
    var broj by remember { mutableStateOf(repo.sljedeciBroj.toString()) }
    var certInfo by remember { mutableStateOf(certStatus(repo.certifikatPostoji())) }
    var poruka by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                repo.spremiCertifikat(bytes)
                certInfo = certStatus(true)
                poruka = "Certifikat učitan (${bytes.size} B). Unesi lozinku i spremi."
            }.onFailure { poruka = "Greška pri učitavanju: ${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Postavke") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Natrag") } },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Podaci obveznika", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = oib, onValueChange = { oib = it.filter(Char::isDigit).take(11) },
                label = { Text("OIB obveznika (11 znamenki)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = oper, onValueChange = { oper = it.filter(Char::isDigit).take(11) },
                label = { Text("OIB operatera (ako je različit od obveznika)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = pdv, onCheckedChange = { pdv = it })
                Spacer(Modifier.width(8.dp))
                Text("Obveznik u sustavu PDV-a")
            }

            Text("Poslovni prostor", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = posPr, onValueChange = { posPr = it },
                label = { Text("Oznaka poslovnog prostora (OznPosPr)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = napUr, onValueChange = { napUr = it },
                label = { Text("Oznaka naplatnog uređaja (OznNapUr)") },
                modifier = Modifier.fillMaxWidth(),
            )
            EnumRedak(
                naslov = "Oznaka slijednosti",
                opcije = OznSlijed.entries.map { it to it.opis },
                odabrano = slijed,
                naOdabir = { slijed = it },
            )

            Divider()
            Text("FINA certifikat (.p12 / .pfx)", style = MaterialTheme.typography.titleMedium)
            Text(certInfo, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    picker.launch(arrayOf("application/x-pkcs12", "application/octet-stream", "*/*"))
                }) { Text("Učitaj certifikat") }
                if (repo.certifikatPostoji()) {
                    OutlinedButton(onClick = {
                        repo.obrisiCertifikat(); certInfo = certStatus(false); lozinka = ""
                        poruka = "Certifikat obrisan."
                    }) { Text("Ukloni") }
                }
            }
            OutlinedTextField(
                value = lozinka, onValueChange = { lozinka = it },
                label = { Text("Lozinka certifikata") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Divider()
            Text("Okolina i numeracija", style = MaterialTheme.typography.titleMedium)
            EnumRedak(
                naslov = "Okolina",
                opcije = FiskalOkolina.entries.map { it to it.opis },
                odabrano = okolina,
                naOdabir = { okolina = it },
            )
            if (okolina == FiskalOkolina.TEST) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = ignoreTls, onCheckedChange = { ignoreTls = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Zanemari TLS provjeru (samo TEST poslužitelj)")
                }
            }
            OutlinedTextField(
                value = broj, onValueChange = { broj = it.filter(Char::isDigit) },
                label = { Text("Sljedeći broj računa (BrOznRac)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            poruka?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    repo.oib = oib; repo.uSustavuPdv = pdv; repo.oznPosPr = posPr
                    repo.oznNapUr = napUr; repo.oznSlijed = slijed; repo.oibOper = oper
                    repo.okolina = okolina; repo.ignoreTlsTrust = ignoreTls
                    repo.certPassword = lozinka
                    repo.sljedeciBroj = broj.toLongOrNull() ?: 1L

                    // Pokušaj validacije certifikata + lozinke radi rane povratne informacije.
                    val info = if (repo.certifikatPostoji() && lozinka.isNotBlank()) {
                        runCatching {
                            FiskalCertificate.load(repo.certifikatBytes()!!.inputStream(), lozinka.toCharArray())
                        }.fold(
                            onSuccess = { "Spremljeno. Certifikat OK (OIB iz cert.: ${it.oibIzCertifikata ?: "?"})." },
                            onFailure = { "Spremljeno, ALI certifikat/lozinka neispravni: ${it.message}" },
                        )
                    } else "Spremljeno."
                    poruka = info
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spremi postavke") }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun certStatus(postoji: Boolean) =
    if (postoji) "Status: certifikat učitan." else "Status: certifikat NIJE učitan."

@Composable
private fun <T> EnumRedak(
    naslov: String,
    opcije: List<Pair<T, String>>,
    odabrano: T,
    naOdabir: (T) -> Unit,
) {
    Column {
        Text(naslov, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opcije.forEach { (vrijednost, opis) ->
                FilterChip(
                    selected = odabrano == vrijednost,
                    onClick = { naOdabir(vrijednost) },
                    label = { Text(opis) },
                )
            }
        }
    }
}
