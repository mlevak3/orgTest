@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Potvrda poslovnog prostora, naplatnog uređaja i broja računa (unaprijed
 * ponuđeni zadani), prije unosa stavki. Korisnik ih po potrebi ručno mijenja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSetupScreen(
    vm: AppViewModel,
    onChangeActivity: (() -> Unit)?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val d = vm.selectedDjelatnost.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novi račun") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Početna") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Djelatnost", style = MaterialTheme.typography.labelLarge)
                    Text(d?.opis() ?: "—", style = MaterialTheme.typography.titleMedium)
                }
                if (onChangeActivity != null) TextButton(onClick = onChangeActivity) { Text("Promijeni") }
            }

            Divider()

            Text("Poslovni prostor", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d?.poslovniProstori?.forEach { p ->
                    FilterChip(
                        selected = vm.selectedProstor.value?.id == p.id,
                        onClick = { vm.odaberiProstor(p) },
                        label = { Text(p.oznaka) },
                    )
                }
            }

            Text("Naplatni uređaj", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.selectedProstor.value?.naplatniUredjaji?.forEach { u ->
                    FilterChip(
                        selected = vm.selectedUredjaj.value?.id == u.id,
                        onClick = { vm.odaberiUredjaj(u) },
                        label = { Text(u.oznaka) },
                    )
                }
            }

            OutlinedTextField(
                value = vm.brojRacuna.value,
                onValueChange = { vm.setBrojRacuna(it) },
                label = { Text("Broj računa") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Račun: ${vm.brojRacuna.value}/${vm.selectedProstor.value?.oznaka ?: ""}/${vm.selectedUredjaj.value?.oznaka ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Nastavi") }
        }
    }
}
