package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.FiskalLogUnos
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalEmptyState
import hr.obrt.fiskal.ui.components.FiskalOutlineButton
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Log fiskalizacije — zahtjev i odgovor svakog pokušaja slanja na CIS (dijagnostika grešaka). */
@Composable
fun FiskalLogScreen(vm: AppViewModel, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    var potvrdaBrisanja by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader(
            "Log fiskalizacije",
            "${vm.fiskalLog.size} zapisa · zahtjev i odgovor CIS-a",
            onBack = onBack,
            akcija = {
                if (vm.fiskalLog.isNotEmpty()) TextButton(onClick = { potvrdaBrisanja = true }) {
                    Text("Obriši", color = t.error)
                }
            },
        )

        if (vm.fiskalLog.isEmpty()) {
            FiskalEmptyState(
                Icons.Rounded.Description,
                "Log je prazan",
                "Ovdje će se pojaviti zahtjev i odgovor svakog pokušaja fiskalizacije.",
                Modifier.padding(horizontal = FiskalSpacing.screenX),
            )
        } else {
            LazyColumn(
                Modifier.padding(horizontal = FiskalSpacing.screenX),
                verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
                contentPadding = PaddingValues(top = FiskalSpacing.stackGap, bottom = FiskalSpacing.listPad),
            ) {
                items(vm.fiskalLog) { unos -> LogRedak(unos) }
            }
        }
    }

    if (potvrdaBrisanja) AlertDialog(
        onDismissRequest = { potvrdaBrisanja = false },
        title = { Text("Obrisati cijeli log?") },
        text = { Text("Zapisi zahtjeva i odgovora fiskalizacije bit će trajno obrisani. Ovo ne utječe na povijest računa.") },
        confirmButton = { TextButton(onClick = { potvrdaBrisanja = false; vm.obrisiFiskalLog() }) { Text("Obriši") } },
        dismissButton = { TextButton(onClick = { potvrdaBrisanja = false }) { Text("Odustani") } },
    )
}

@Composable
private fun LogRedak(unos: FiskalLogUnos) {
    val t = LocalFiskalTokens.current
    val ctx = LocalContext.current
    var prosiren by remember { mutableStateOf(false) }
    val uspjeh = unos.ishod.startsWith("Fiskaliziran")
    val bojaIshoda = if (uspjeh) t.success else t.error

    FiskalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FiskalSpacing.card)) {
            Row(
                Modifier.fillMaxWidth().clickable { prosiren = !prosiren },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Račun ${unos.brojRacuna}", style = MaterialTheme.typography.bodyLarge, color = t.ink)
                        if (unos.nakDost) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "NAKDOST",
                                style = MaterialTheme.typography.labelMedium,
                                color = t.terracottaTintInk,
                                modifier = Modifier
                                    .background(t.terracottaTint, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(Date(unos.vrijeme)) +
                            " · HTTP ${if (unos.httpKod >= 0) unos.httpKod else "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.muted,
                    )
                    Text(unos.ishod, style = MaterialTheme.typography.bodySmall, color = bojaIshoda, fontWeight = FontWeight.Medium)
                }
                Icon(
                    if (prosiren) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    "Prikaži/sakrij", tint = t.mutedSoft,
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = prosiren,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
            ) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    XmlBlok("Zahtjev (SOAP)", unos.requestXml, ctx)
                    XmlBlok("Odgovor CIS-a", unos.responseXml.ifBlank { "(nema odgovora — pogledaj poruku iznad)" }, ctx)
                }
            }
        }
    }
}

@Composable
private fun XmlBlok(naziv: String, sadrzaj: String, ctx: android.content.Context) {
    val t = LocalFiskalTokens.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(naziv, style = MaterialTheme.typography.labelLarge, color = t.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = { InvoiceShare.copyToClipboard(ctx, naziv, sadrzaj) }) {
                Icon(Icons.Rounded.ContentCopy, "Kopiraj $naziv", tint = t.mutedSoft, modifier = Modifier.size(18.dp))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(t.surfaceSunken, androidx.compose.foundation.shape.RoundedCornerShape(FiskalSpacing.stackGap))
                .padding(10.dp),
        ) {
            SelectionContainer {
                Text(
                    sadrzaj,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = t.ink,
                )
            }
        }
    }
}
