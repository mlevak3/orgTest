package hr.obrt.fiskal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.TemaAplikacije
import hr.obrt.fiskal.ui.theme.FiskalRadius
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

/** Hero zaglavlje Početne — tvrtka-chip + toggle teme, PROMET DANAS + info-chipovi. BRAND-UPUTE 8.15/9.1. */
@Composable
fun HeroHeader(
    tvrtkaNaziv: String,
    okolinaLabel: String,
    tema: TemaAplikacije,
    onToggleTema: () -> Unit,
    onCompanies: () -> Unit,
    onSettings: () -> Unit,
    prometDanasText: String,
    brojDanas: Int,
    prometMjesecText: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalFiskalTokens.current
    Column(
        modifier
            .fillMaxWidth()
            .background(t.heroHeader, RoundedCornerShape(bottomStart = FiskalRadius.hero, bottomEnd = FiskalRadius.hero))
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(FiskalRadius.pill))
                    .clickable(onClick = onCompanies)
                    .background(t.oliveInk.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Storefront, null, tint = t.oliveInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tvrtkaNaziv, color = t.oliveInk, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(FiskalRadius.pill)).background(t.oliveInk.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp),
                ) { Text(okolinaLabel, color = t.oliveInk, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, "Promijeni tvrtku", tint = t.oliveInk, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(t.oliveInk.copy(alpha = 0.12f)).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Settings, "Postavke tvrtke", tint = t.oliveInk, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(t.oliveInk.copy(alpha = 0.12f)).clickable(onClick = onToggleTema),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (tema) {
                        TemaAplikacije.SUSTAV -> Icons.Rounded.Brightness6
                        TemaAplikacije.SVIJETLA -> Icons.Rounded.Brightness7
                        TemaAplikacije.TAMNA -> Icons.Rounded.Brightness4
                    },
                    "Tema", tint = t.oliveInk, modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("PROMET DANAS", color = t.oliveInk.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(prometDanasText, color = t.oliveInk, style = MaterialTheme.typography.displaySmall)

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("$brojDanas računa danas", t)
            InfoChip("Mjesec: $prometMjesecText", t)
        }
    }
}

@Composable
private fun InfoChip(text: String, t: hr.obrt.fiskal.ui.theme.FiskalTokens) {
    Box(
        Modifier.clip(RoundedCornerShape(FiskalRadius.pill)).background(t.oliveInk.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = t.oliveInk, style = MaterialTheme.typography.bodySmall) }
}
