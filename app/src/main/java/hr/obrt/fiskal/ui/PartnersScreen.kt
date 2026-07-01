package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Partner
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalEmptyState
import hr.obrt.fiskal.ui.components.FiskalTerracottaButton
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.SearchPill
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

@Composable
fun PartnersScreen(vm: AppViewModel, onPick: ((Partner) -> Unit)?, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    var q by remember { mutableStateOf("") }
    val filtrirani = vm.partners.filter {
        q.isBlank() || it.naziv.contains(q, true) || it.oib.contains(q, true)
    }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(Modifier.fillMaxSize()) {
            LightHeader(
                if (onPick != null) "Odaberi partnera" else "Partneri",
                "${vm.partners.size} partnera u šifrarniku",
                onBack = onBack,
            )

            Box(Modifier.padding(horizontal = FiskalSpacing.screenX)) {
                SearchPill(q, { q = it }, "Pretraži partnere…")
            }
            Spacer(Modifier.height(FiskalSpacing.stackGap))

            if (filtrirani.isEmpty()) {
                FiskalEmptyState(
                    Icons.Rounded.Group,
                    if (vm.partners.isEmpty()) "Šifrarnik je prazan" else "Nema rezultata",
                    if (vm.partners.isEmpty()) "Dodaj prvog partnera gumbom ispod." else "Pokušaj drugi pojam pretrage.",
                    Modifier.padding(horizontal = FiskalSpacing.screenX),
                )
            } else {
                LazyColumn(
                    Modifier.padding(horizontal = FiskalSpacing.screenX),
                    verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
                    contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
                ) {
                    itemsIndexed(filtrirani) { index, p ->
                        val pick = onPick
                        FiskalCard(Modifier.fillMaxWidth(), onClick = { if (pick != null) pick(p) else vm.editPartner(p) }) {
                            Row(
                                Modifier.padding(FiskalSpacing.card).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                InicijaliAvatar(p.naziv, index % 2 == 0)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.naziv.ifBlank { "(bez naziva)" }, style = MaterialTheme.typography.bodyLarge, color = t.ink)
                                    Text(
                                        "OIB ${p.oib.ifBlank { "—" }}" + (if (p.adresa.isNotBlank()) " · ${p.adresa}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = t.muted,
                                    )
                                }
                                if (pick == null) Icon(
                                    Icons.Rounded.Edit, "Uredi", tint = t.mutedSoft,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        FiskalTerracottaButton(
            "Novi partner",
            onClick = { vm.newPartner() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(FiskalSpacing.screenX),
        )
    }

    if (vm.editingPartner.value != null) PartnerDialog(vm)
}

@Composable
private fun InicijaliAvatar(naziv: String, olive: Boolean) {
    val t = LocalFiskalTokens.current
    val inicijali = naziv.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifBlank { "?" }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (olive) t.oliveTint else t.terracottaTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            inicijali,
            color = if (olive) t.oliveTintInk else t.terracottaTintInk,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
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
