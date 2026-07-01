package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.data.TemaAplikacije
import hr.obrt.fiskal.fiskal.InvoiceShare
import hr.obrt.fiskal.fiskal.ReceiptPrinter
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.HeroHeader
import hr.obrt.fiskal.ui.components.IconTile
import hr.obrt.fiskal.ui.components.StatusBadge
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: AppViewModel,
    tema: TemaAplikacije,
    onToggleTema: () -> Unit,
    onHistory: () -> Unit,
    onArticles: () -> Unit,
    onSettings: () -> Unit,
    onCompanies: () -> Unit,
    onBackup: () -> Unit,
    onOpenInvoice: (SavedInvoice) -> Unit,
) {
    val t = LocalFiskalTokens.current
    val ctx = LocalContext.current
    val tvrtka = vm.selected.value
    val (brojDanas, prometDanas) = vm.statistikaDanas()
    val prometMjesec = vm.izvjestaj(PeriodIzvjestaja.MJESEC).ukupanPromet
    val zadnji = vm.zadnjiRacun()

    Column(Modifier.fillMaxSize().background(t.bg).verticalScroll(rememberScrollState())) {
        HeroHeader(
            tvrtkaNaziv = tvrtka?.opis() ?: "Odaberi tvrtku",
            okolinaLabel = tvrtka?.okolina?.opis?.take(4)?.uppercase(Locale.ROOT) ?: "—",
            tema = tema,
            onToggleTema = onToggleTema,
            onCompanies = onCompanies,
            prometDanasText = hrEur(prometDanas),
            brojDanas = brojDanas,
            prometMjesecText = hrEur(prometMjesec),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = FiskalSpacing.screenX, vertical = FiskalSpacing.stackGap),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Zadnji račun", style = MaterialTheme.typography.titleSmall, color = t.ink, modifier = Modifier.weight(1f))
                Text(
                    "Svi računi",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.terracotta,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable(onClick = onHistory),
                )
            }

            if (zadnji != null) {
                FiskalCard(Modifier.fillMaxWidth(), onClick = { onOpenInvoice(zadnji) }) {
                    Column(Modifier.padding(horizontal = FiskalSpacing.card, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Račun ${zadnji.brojRacuna()}", style = MaterialTheme.typography.titleMedium, color = t.ink, modifier = Modifier.weight(1f))
                            Text(hrEur(zadnji.racun.iznosUkupno), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = t.ink)
                        }
                        Text(
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date(zadnji.createdAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.muted,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(zadnji.jir != null)
                            Spacer(Modifier.weight(1f))
                            KrugGumb(Icons.Rounded.Print) { ReceiptPrinter.print(ctx, vm.receiptFromSaved(zadnji)) }
                            Spacer(Modifier.width(8.dp))
                            KrugGumb(Icons.Rounded.Share) { InvoiceShare.sharePdf(ctx, vm.receiptFromSaved(zadnji)) }
                        }
                    }
                }
            } else {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Još nema računa. Novi račun kreiraš gumbom + u donjoj navigaciji.",
                        Modifier.padding(FiskalSpacing.card),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.muted,
                    )
                }
            }

            Text("Ostalo", style = MaterialTheme.typography.titleSmall, color = t.ink)
            FiskalCard(Modifier.fillMaxWidth()) {
                Column {
                    OstaloRedak("Šifrarnik artikala", Icons.Rounded.Inventory2, onArticles)
                    androidx.compose.material3.Divider(color = t.border, modifier = Modifier.padding(horizontal = FiskalSpacing.card))
                    OstaloRedak("Postavke tvrtke", Icons.Rounded.Settings, onSettings)
                    androidx.compose.material3.Divider(color = t.border, modifier = Modifier.padding(horizontal = FiskalSpacing.card))
                    OstaloRedak("Sigurnosna kopija", Icons.Rounded.CloudUpload, onBackup)
                }
            }
            Spacer(Modifier.height(FiskalSpacing.listPad))
        }
    }
}

@Composable
private fun KrugGumb(ikona: ImageVector, onClick: () -> Unit) {
    val t = LocalFiskalTokens.current
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(t.oliveTint).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(ikona, null, tint = t.oliveTintInk, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun OstaloRedak(naslov: String, ikona: ImageVector, onClick: () -> Unit) {
    val t = LocalFiskalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = FiskalSpacing.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(ikona, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(naslov, style = MaterialTheme.typography.bodyLarge, color = t.ink, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedSoft)
    }
}
