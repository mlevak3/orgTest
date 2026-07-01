package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Djelatnost

/** Odabir djelatnosti prije izrade novog računa (prikazuje se samo ako tvrtka ima više njih). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityPickerScreen(vm: AppViewModel, onPicked: (Djelatnost) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tvrtka = vm.selected.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Odaberi djelatnost") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Početna") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tvrtka?.djelatnosti ?: emptyList()) { d ->
                val zadana = tvrtka?.zadanaDjelatnostId == d.id
                ElevatedCard(onClick = { onPicked(d) }) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.opis(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${d.poslovniProstori.size} posl. prostor(a) · " +
                                    "zadano: ${d.zadaniProstor().oznaka}/${d.zadaniUredjaj().oznaka}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (zadana) AssistChip(onClick = {}, enabled = false, label = { Text("Zadano") })
                    }
                }
            }
        }
    }
}
