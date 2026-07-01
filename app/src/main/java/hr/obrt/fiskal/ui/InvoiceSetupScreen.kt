@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.ui.components.FiskalChip
import hr.obrt.fiskal.ui.components.FiskalPrimaryButton
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

/**
 * Potvrda poslovnog prostora, naplatnog uređaja i broja računa (unaprijed
 * ponuđeni zadani), prije unosa stavki. Korisnik ih po potrebi ručno mijenja.
 */
@Composable
fun InvoiceSetupScreen(
    vm: AppViewModel,
    onChangeActivity: (() -> Unit)?,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalFiskalTokens.current
    val d = vm.selectedDjelatnost.value

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Novi račun", onBack = onBack)

        Column(
            Modifier.padding(horizontal = FiskalSpacing.screenX).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Djelatnost", style = MaterialTheme.typography.labelMedium, color = t.muted)
                    Text(d?.opis() ?: "—", style = MaterialTheme.typography.titleMedium, color = t.ink)
                }
                if (onChangeActivity != null) TextButton(onClick = onChangeActivity) { Text("Promijeni") }
            }

            Text("Poslovni prostor", style = MaterialTheme.typography.labelMedium, color = t.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d?.poslovniProstori?.forEach { p ->
                    FiskalChip(p.oznaka, vm.selectedProstor.value?.id == p.id) { vm.odaberiProstor(p) }
                }
            }

            Text("Naplatni uređaj", style = MaterialTheme.typography.labelMedium, color = t.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.selectedProstor.value?.naplatniUredjaji?.forEach { u ->
                    FiskalChip(u.oznaka, vm.selectedUredjaj.value?.id == u.id) { vm.odaberiUredjaj(u) }
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
                color = t.muted,
            )

            FiskalPrimaryButton("Nastavi", onClick = onContinue, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(FiskalSpacing.screenX))
        }
    }
}
