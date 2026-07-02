package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.data.TemaAplikacije
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.HeroHeader
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
    onSettings: () -> Unit,
    onCompanies: () -> Unit,
    onOpenInvoice: (SavedInvoice) -> Unit,
) {
    val t = LocalFiskalTokens.current
    val tvrtka = vm.selected.value
    val (brojDanas, prometDanas) = vm.statistikaDanas()
    val prometMjesec = vm.izvjestaj(PeriodIzvjestaja.MJESEC).ukupanPromet
    val sazetak = vm.pocetnaSazetak()

    Column(Modifier.fillMaxSize().background(t.bg).verticalScroll(rememberScrollState())) {
        HeroHeader(
            tvrtkaNaziv = tvrtka?.opis() ?: "Odaberi tvrtku",
            okolinaLabel = tvrtka?.okolina?.opis?.take(4)?.uppercase(Locale.ROOT) ?: "—",
            tema = tema,
            onToggleTema = onToggleTema,
            onCompanies = onCompanies,
            onSettings = onSettings,
            prometDanasText = hrEur(prometDanas),
            brojDanas = brojDanas,
            prometMjesecText = hrEur(prometMjesec),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = FiskalSpacing.screenX, vertical = FiskalSpacing.stackGap),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
        ) {
            if (sazetak.nefiskBroj > 0) {
                NefiskUpozorenje(sazetak.nefiskBroj, hrEur(sazetak.nefiskIznos), onHistory)
            }

            if (sazetak.poDanima.any { it.iznos.signum() != 0 }) {
                PrometGrafKartica(sazetak.poDanima, sazetak.trendPostotak)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Zadnji računi", style = MaterialTheme.typography.titleSmall, color = t.ink, modifier = Modifier.weight(1f))
                Text(
                    "Svi računi",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.terracotta,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.clickable(onClick = onHistory),
                )
            }

            if (sazetak.zadnji.isEmpty()) {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Još nema računa. Novi račun kreiraš gumbom + u donjoj navigaciji.",
                        Modifier.padding(FiskalSpacing.card),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.muted,
                    )
                }
            } else {
                FiskalCard(Modifier.fillMaxWidth()) {
                    Column {
                        sazetak.zadnji.forEachIndexed { index, si ->
                            RacunRedak(si) { onOpenInvoice(si) }
                            if (index < sazetak.zadnji.lastIndex) {
                                androidx.compose.material3.Divider(color = t.border, modifier = Modifier.padding(horizontal = FiskalSpacing.card))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(FiskalSpacing.listPad))
        }
    }
}

/** Upozorenje o nefiskaliziranim računima (prikazuje se samo kad ih ima). */
@Composable
private fun NefiskUpozorenje(broj: Int, iznosText: String, onClick: () -> Unit) {
    val t = LocalFiskalTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FiskalSpacing.card))
            .background(t.errorBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(t.terracottaTint),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PriorityHigh, null, tint = t.error, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nefiskTekst(broj), style = MaterialTheme.typography.bodyLarge, color = t.error)
            Text("Ukupno $iznosText čeka fiskalizaciju", style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = t.error)
    }
}

private fun nefiskTekst(n: Int): String = when {
    n == 1 -> "1 račun nije fiskaliziran"
    n in 2..4 -> "$n računa nisu fiskalizirana"
    else -> "$n računa nije fiskalizirano"
}

/** Kartica s grafom prometa po danima (zadnjih 7 dana) i trendom. */
@Composable
private fun PrometGrafKartica(dani: List<DanPromet>, trend: Int?) {
    val t = LocalFiskalTokens.current
    val danas = dani.lastOrNull()
    FiskalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FiskalSpacing.card)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("Promet · 7 dana", style = MaterialTheme.typography.titleSmall, color = t.ink, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (trend != null) {
                        val gore = trend >= 0
                        Text(
                            (if (gore) "▲ " else "▼ ") + "${kotlin.math.abs(trend)}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (gore) t.success else t.error,
                        )
                    }
                    danas?.let { Text(hrEur(it.iznos), style = MaterialTheme.typography.bodySmall, color = t.muted) }
                }
            }
            Spacer(Modifier.height(12.dp))
            StupciGraf(dani)
        }
    }
}

@Composable
private fun StupciGraf(dani: List<DanPromet>) {
    val t = LocalFiskalTokens.current
    val maks = dani.maxOfOrNull { it.iznos }?.takeIf { it.signum() > 0 } ?: java.math.BigDecimal.ONE
    val visinaZone = 90.dp
    Row(
        Modifier.fillMaxWidth().height(visinaZone + 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        dani.forEach { d ->
            val udio = (d.iznos.toFloat() / maks.toFloat()).coerceIn(0f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(visinaZone).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    // Minimalna vidljiva visina i za nulti/vrlo mali promet.
                    val visina = (visinaZone.value * udio).coerceAtLeast(6f).dp
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(visina)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (d.jeDanas) t.olive else t.sage.copy(alpha = 0.45f)),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    d.labela,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.jeDanas) t.ink else t.muted,
                    fontWeight = if (d.jeDanas) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RacunRedak(si: SavedInvoice, onClick: () -> Unit) {
    val t = LocalFiskalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = FiskalSpacing.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Račun ${si.brojRacuna()}", style = MaterialTheme.typography.bodyLarge, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                SimpleDateFormat("dd.MM. HH:mm", Locale.ROOT).format(Date(si.createdAt)) + " · " + si.racun.nacinPlac.opis,
                style = MaterialTheme.typography.bodySmall,
                color = t.muted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusTocka(fiskaliziran = si.jir != null)
        Spacer(Modifier.width(8.dp))
        Text(hrEur(si.racun.iznosUkupno), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 1, softWrap = false)
    }
}

/** Točka statusa: zelena = fiskaliziran, terakota = nije. */
@Composable
private fun StatusTocka(fiskaliziran: Boolean) {
    val t = LocalFiskalTokens.current
    Box(Modifier.size(9.dp).clip(CircleShape).background(if (fiskaliziran) t.success else t.terracotta))
}
