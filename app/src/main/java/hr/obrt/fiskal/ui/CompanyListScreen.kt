package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Tvrtka

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyListScreen(
    vm: AppViewModel,
    onSelect: (Tvrtka) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Tvrtka) -> Unit,
    onBack: (() -> Unit)?,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Odaberi tvrtku") },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("Natrag") }
                },
                actions = { TextButton(onClick = onAdd) { Text("Dodaj") } },
            )
        }
    ) { pad ->
        if (vm.companies.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Još nema nijedne tvrtke.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAdd) { Text("Dodaj prvu tvrtku") }
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.companies) { t ->
                val odabrana = vm.selected.value?.id == t.id
                Card(onClick = { onSelect(t) }) {
                    Row(
                        Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (odabrana) "✓ " else "") + t.opis(),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "OIB ${t.oib.ifBlank { "—" }} · ${t.okolina.opis} · sl. račun ${t.sljedeciBroj}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onEdit(t) }) { Text("Uredi") }
                    }
                }
            }
        }
    }
}
