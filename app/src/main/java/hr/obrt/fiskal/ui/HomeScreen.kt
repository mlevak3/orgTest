package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onNewInvoice: () -> Unit,
    onHistory: () -> Unit,
    onArticles: () -> Unit,
    onSettings: () -> Unit,
    onCompanies: () -> Unit,
    onBackup: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fiskal Obrt", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = cs.primary,
                    titleContentColor = cs.onPrimary,
                ),
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Istaknuta odabrana tvrtka + promjena
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Business, null, Modifier.size(34.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ODABRANA TVRTKA", style = MaterialTheme.typography.labelSmall)
                        Text(
                            vm.selected.value?.opis() ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            "OIB ${vm.selected.value?.oib ?: "—"} · ${vm.selected.value?.okolina?.opis ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FilledTonalButton(onClick = onCompanies) { Text("Promijeni") }
                }
            }

            Spacer(Modifier.height(4.dp))

            MenuKartica("Novi račun", "Unesi i fiskaliziraj račun", Icons.Filled.Add, cs.primary, cs.onPrimary, onNewInvoice)
            MenuKartica("Pregled računa", "Povijest, ispis i email", Icons.Filled.List, cs.secondaryContainer, cs.onSecondaryContainer, onHistory)
            MenuKartica("Šifrarnik artikala", "Spremljeni artikli i usluge", Icons.Filled.ShoppingCart, cs.tertiaryContainer, cs.onTertiaryContainer, onArticles)
            MenuKartica("Postavke tvrtke", "Certifikat, prostor, numeracija", Icons.Filled.Settings, cs.surfaceVariant, cs.onSurfaceVariant, onSettings)
            MenuKartica("Sigurnosna kopija", "Izvoz/uvoz svih podataka", Icons.Filled.CloudUpload, cs.surfaceVariant, cs.onSurfaceVariant, onBackup)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuKartica(
    naslov: String,
    opis: String,
    ikona: ImageVector,
    pozadina: Color,
    naPozadini: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = pozadina, contentColor = naPozadini),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(ikona, null, Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(naslov, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(opis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
