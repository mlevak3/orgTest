package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Artikl
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(vm: AppViewModel, onPick: ((Artikl) -> Unit)?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onPick != null) "Odaberi artikl" else "Šifrarnik artikala") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Natrag") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.newArticle() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Novi artikl") },
            )
        },
    ) { pad ->
        if (vm.articles.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Šifrarnik je prazan. Dodaj artikl (+).", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.articles) { a ->
                    val pick = onPick
                    ElevatedCard(onClick = { if (pick != null) pick(a) else vm.editArticle(a) }) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.naziv.ifBlank { "(bez naziva)" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${a.jedCijena.toPlainString()} € / ${a.jedMjere} · PDV ${a.pdvStopa.toPlainString()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (pick != null) {
                                AssistChip(onClick = { pick(a) }, label = { Text("Dodaj") })
                            } else {
                                IconButton(onClick = { vm.editArticle(a) }) { Icon(Icons.Filled.Edit, "Uredi") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(naziv, { naziv = it }, label = { Text("Naziv") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(jed, { jed = it }, label = { Text("Jed. mjere") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        stopa, { stopa = it }, label = { Text("PDV %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    cijena, { cijena = it }, label = { Text("Cijena (neto, €)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
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
