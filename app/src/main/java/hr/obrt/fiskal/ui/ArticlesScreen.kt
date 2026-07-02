package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Artikl
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalEmptyState
import hr.obrt.fiskal.ui.components.FiskalInput
import hr.obrt.fiskal.ui.components.FiskalTerracottaButton
import hr.obrt.fiskal.ui.components.IconTile
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.SearchPill
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import java.math.BigDecimal

@Composable
fun ArticlesScreen(vm: AppViewModel, onPick: ((Artikl) -> Unit)?, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    var q by remember { mutableStateOf("") }
    val filtrirani = vm.articles.filter { q.isBlank() || it.naziv.contains(q, ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(t.bg)) {
        Column(Modifier.fillMaxSize()) {
            LightHeader(
                if (onPick != null) "Odaberi artikl" else "Artikli",
                "${vm.articles.size} artikla u šifrarniku",
                onBack = onBack,
            )

            Box(Modifier.padding(horizontal = FiskalSpacing.screenX)) {
                SearchPill(q, { q = it }, "Pretraži artikle…")
            }
            Spacer(Modifier.height(FiskalSpacing.stackGap))

            if (filtrirani.isEmpty()) {
                FiskalEmptyState(
                    Icons.Rounded.Inventory2,
                    if (vm.articles.isEmpty()) "Šifrarnik je prazan" else "Nema rezultata",
                    if (vm.articles.isEmpty()) "Dodaj prvi artikl gumbom ispod." else "Pokušaj drugi pojam pretrage.",
                    Modifier.padding(horizontal = FiskalSpacing.screenX),
                )
            } else {
                LazyColumn(
                    Modifier.padding(horizontal = FiskalSpacing.screenX),
                    verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
                    contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
                ) {
                    items(filtrirani) { a ->
                        val pick = onPick
                        FiskalCard(Modifier.fillMaxWidth(), onClick = { if (pick != null) pick(a) else vm.editArticle(a) }) {
                            Row(
                                Modifier.padding(FiskalSpacing.card).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconTile(Icons.Rounded.Sell)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.naziv.ifBlank { "(bez naziva)" }, style = MaterialTheme.typography.bodyLarge, color = t.ink)
                                    Text(
                                        "${hrEur(a.jedCijena)} / ${a.jedMjere} · PDV ${a.pdvStopa.toPlainString()}%",
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

        // Podignut iznad donje navigacije (ona se crta preko ovog ekrana) da ostane vidljiv.
        FiskalTerracottaButton(
            "Novi artikl",
            onClick = { vm.newArticle() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = FiskalSpacing.screenX, bottom = FiskalSpacing.listPad),
        )
    }

    if (vm.editingArticle.value != null) ArtiklDialog(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtiklDialog(vm: AppViewModel) {
    val a = vm.editingArticle.value ?: return
    val postoji = vm.articles.any { it.id == a.id }
    var naziv by remember { mutableStateOf(a.naziv) }
    var jed by remember { mutableStateOf(a.jedMjere) }
    var cijena by remember { mutableStateOf(if (a.jedCijena.signum() == 0) "" else a.jedCijena.toPlainString()) }
    var stopa by remember { mutableStateOf(a.pdvStopa.toPlainString()) }

    AlertDialog(
        onDismissRequest = { vm.editingArticle.value = null },
        title = { Text(if (postoji) "Uredi artikl" else "Novi artikl") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FiskalInput(naziv, { naziv = it }, "Naziv artikla", Modifier.fillMaxWidth(), placeholder = "npr. Med bagremov")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FiskalInput(jed, { jed = it }, "Jed. mjere", Modifier.weight(1f), placeholder = "kom")
                    FiskalInput(
                        stopa, { stopa = it }, "PDV %", Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                FiskalInput(
                    cijena, { cijena = it }, "Cijena (neto, €)", Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveArticle(
                    a.copy(
                        naziv = naziv,
                        jedMjere = jed.ifBlank { "kom" },
                        jedCijena = cijena.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        pdvStopa = stopa.replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    )
                )
            }) { Text("Spremi") }
        },
        dismissButton = {
            Row {
                if (postoji) TextButton(onClick = { vm.deleteArticle(a) }) { Text("Obriši") }
                TextButton(onClick = { vm.editingArticle.value = null }) { Text("Odustani") }
            }
        },
    )
}
