package hr.obrt.fiskal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.data.TemaAplikacije
import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    tema: TemaAplikacije,
    onToggleTema: () -> Unit,
    onNewInvoice: () -> Unit,
    onHistory: () -> Unit,
    onArticles: () -> Unit,
    onSettings: () -> Unit,
    onCompanies: () -> Unit,
    onBackup: () -> Unit,
    onOpenInvoice: (SavedInvoice) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val tvrtka = vm.selected.value
    val (brojDanas, prometDanas) = vm.statistikaDanas()
    val zadnji = vm.zadnjiRacun()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fiskal Obrt", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onToggleTema) {
                        Icon(
                            when (tema) {
                                TemaAplikacije.SUSTAV -> Icons.Filled.Brightness6
                                TemaAplikacije.SVIJETLA -> Icons.Filled.Brightness7
                                TemaAplikacije.TAMNA -> Icons.Filled.Brightness4
                            },
                            "Tema: ${tema.opis}",
                            tint = cs.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = cs.primary,
                    titleContentColor = cs.onPrimary,
                ),
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Odabrana tvrtka
            Card(
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Business, null, Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ODABRANA TVRTKA", style = MaterialTheme.typography.labelSmall)
                        Text(tvrtka?.opis() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "OIB ${tvrtka?.oib ?: "—"} · ${tvrtka?.okolina?.opis ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onCompanies) { Text("Promijeni") }
                }
            }

            // Promet danas
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer)) {
                Row(Modifier.padding(16.dp).fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Računa danas", style = MaterialTheme.typography.labelMedium)
                        Text("$brojDanas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Promet danas", style = MaterialTheme.typography.labelMedium)
                        Text("${prometDanas.toPlainString()} €", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Glavna radnja
            Button(
                onClick = onNewInvoice,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("+ Novi račun", style = MaterialTheme.typography.titleMedium) }

            // Zadnji račun
            if (zadnji != null) {
                ElevatedCard(onClick = { onOpenInvoice(zadnji) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Zadnji račun", style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Račun ${zadnji.brojRacuna()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${FiskalFormat.amount(zadnji.racun.iznosUkupno)} €", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(zadnji.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { ReceiptPrinter.print(ctx, vm.receiptFromSaved(zadnji)) }) {
                                Icon(Icons.Filled.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ispiši")
                            }
                            TextButton(onClick = { InvoiceShare.sharePdf(ctx, vm.receiptFromSaved(zadnji)) }) {
                                Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Podijeli")
                            }
                        }
                    }
                }
            }

            // Brze radnje — mreža 2x2
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuPlocica("Računi", Icons.Filled.List, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f), onHistory)
                MenuPlocica("Šifrarnik", Icons.Filled.ShoppingCart, cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f), onArticles)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuPlocica("Postavke", Icons.Filled.Settings, cs.surfaceVariant, cs.onSurfaceVariant, Modifier.weight(1f), onSettings)
                MenuPlocica("Sig. kopija", Icons.Filled.CloudUpload, cs.surfaceVariant, cs.onSurfaceVariant, Modifier.weight(1f), onBackup)
            }
        }
    }
}

@Composable
private fun MenuPlocica(
    naslov: String,
    ikona: ImageVector,
    pozadina: Color,
    naPozadini: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = pozadina, contentColor = naPozadini),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(ikona, null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(naslov, style = MaterialTheme.typography.labelLarge)
        }
    }
}
