package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.fiskal.FiskalFormat
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (SavedInvoice) -> Unit, onBack: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Računi — ${vm.selected.value?.opis() ?: ""}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Natrag") } },
            )
        }
    ) { pad ->
        if (vm.history.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Još nema spremljenih računa.")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.history) { si ->
                Card(onClick = { onOpen(si) }) {
                    Column(Modifier.padding(14.dp).fillMaxWidth()) {
                        Row {
                            Text("Račun ${si.brojRacuna()}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.weight(1f))
                            Text("${FiskalFormat.amount(si.racun.iznosUkupno)} €", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(fmt.format(java.util.Date(si.createdAt)), style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (si.jir != null) "JIR: ${si.jir}" else "⚠ ${si.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (si.jir != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
