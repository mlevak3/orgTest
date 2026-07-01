package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Partner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnersScreen(vm: AppViewModel, onPick: ((Partner) -> Unit)?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            val cs = MaterialTheme.colorScheme
            TopAppBar(
                title = { Text(if (onPick != null) "Odaberi partnera" else "Šifrarnik partnera") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Natrag") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.newPartner() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Novi partner") },
            )
        },
    ) { pad ->
        var q by remember { mutableStateOf("") }
        val filtrirani = vm.partners.filter {
            q.isBlank() || it.naziv.contains(q, true) || it.oib.contains(q, true)
        }

        if (vm.partners.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Šifrarnik je prazan. Dodaj partnera (+).", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(Modifier.padding(pad)) {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text("Pretraži partnere") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                if (filtrirani.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nema rezultata.") }
                } else LazyColumn(
                    Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtrirani) { p ->
                        val pick = onPick
                        ElevatedCard(onClick = { if (pick != null) pick(p) else vm.editPartner(p) }) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.naziv.ifBlank { "(bez naziva)" }, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "OIB ${p.oib.ifBlank { "—" }}" + (if (p.adresa.isNotBlank()) " · ${p.adresa}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (pick != null) {
                                    AssistChip(onClick = { pick(p) }, label = { Text("Odaberi") })
                                } else {
                                    IconButton(onClick = { vm.editPartner(p) }) { Icon(Icons.Filled.Edit, "Uredi") }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (vm.editingPartner.value != null) PartnerDialog(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerDialog(vm: AppViewModel) {
    val p = vm.editingPartner.value ?: return
    val postoji = vm.partners.any { it.id == p.id }
    var naziv by remember { mutableStateOf(p.naziv) }
    var oib by remember { mutableStateOf(p.oib) }
    var adresa by remember { mutableStateOf(p.adresa) }

    AlertDialog(
        onDismissRequest = { vm.editingPartner.value = null },
        title = { Text(if (postoji) "Uredi partnera" else "Novi partner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(naziv, { naziv = it }, label = { Text("Naziv") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    oib, { oib = it.filter(Char::isDigit).take(11) },
                    label = { Text("OIB") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(adresa, { adresa = it }, label = { Text("Adresa") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.savePartner(p.copy(naziv = naziv, oib = oib, adresa = adresa)) }) { Text("Spremi") }
        },
        dismissButton = {
            Row {
                if (postoji) TextButton(onClick = { vm.deletePartner(p) }) { Text("Obriši") }
                TextButton(onClick = { vm.editingPartner.value = null }) { Text("Odustani") }
            }
        },
    )
}
