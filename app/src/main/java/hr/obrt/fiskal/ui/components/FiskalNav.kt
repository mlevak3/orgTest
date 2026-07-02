package hr.obrt.fiskal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

enum class NavTab { POCETNA, RACUNI, ARTIKLI, PARTNERI, IZVJESTAJI }

/** Donja navigacija — tabovi s centralnim terakota FAB-om za novi račun. BRAND-UPUTE 8.14. */
@Composable
fun FiskalBottomNav(
    current: NavTab,
    onPocetna: () -> Unit,
    onRacuni: () -> Unit,
    onArtikli: () -> Unit,
    onPartneri: () -> Unit,
    onIzvjestaji: () -> Unit,
    onNoviRacun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalFiskalTokens.current
    Box(modifier.fillMaxWidth().height(92.dp)) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .background(t.surface)
                .border(androidx.compose.foundation.BorderStroke(1.dp, t.border))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lijeva dva taba imaju weight 1.5f, desna tri 1f — zbroj težina s obje
            // strane FAB praznine je jednak pa FAB ostaje točno na sredini.
            NavItem(Icons.Rounded.Home, "Početna", current == NavTab.POCETNA, onPocetna, Modifier.weight(1.5f))
            NavItem(Icons.Rounded.ReceiptLong, "Računi", current == NavTab.RACUNI, onRacuni, Modifier.weight(1.5f))
            Spacer(Modifier.width(60.dp))
            NavItem(Icons.Rounded.Inventory2, "Artikli", current == NavTab.ARTIKLI, onArtikli, Modifier.weight(1f))
            NavItem(Icons.Rounded.Group, "Partneri", current == NavTab.PARTNERI, onPartneri, Modifier.weight(1f))
            NavItem(Icons.Rounded.BarChart, "Izvještaji", current == NavTab.IZVJESTAJI, onIzvjestaji, Modifier.weight(1f))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(56.dp)
                .shadow(10.dp, CircleShape, spotColor = t.terracotta.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(t.terracotta)
                .clickable(onClick = onNoviRacun),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, "Novi račun", tint = t.terracottaInk, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, aktivan: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    val boja = if (aktivan) t.olive else t.mutedSoft
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = boja, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = boja,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}
