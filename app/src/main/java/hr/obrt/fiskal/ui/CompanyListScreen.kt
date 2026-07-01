package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.Tvrtka
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.MiniBadge
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

@Composable
fun CompanyListScreen(
    vm: AppViewModel,
    onSelect: (Tvrtka) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Tvrtka) -> Unit,
    onBack: (() -> Unit)?,
) {
    val t = LocalFiskalTokens.current

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Odaberi tvrtku", "${vm.companies.size} tvrtki", onBack = onBack)

        if (vm.companies.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Još nema nijedne tvrtke.", style = MaterialTheme.typography.titleMedium, color = t.ink)
                Spacer(Modifier.height(12.dp))
                hr.obrt.fiskal.ui.components.FiskalPrimaryButton("Dodaj prvu tvrtku", onClick = onAdd)
            }
            return@Column
        }

        LazyColumn(
            Modifier.padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
            contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
        ) {
            items(vm.companies) { comp ->
                val aktivna = vm.selected.value?.id == comp.id
                FiskalCard(
                    Modifier
                        .fillMaxWidth()
                        .then(if (aktivna) Modifier.border(1.5.dp, t.terracotta, RoundedCornerShape(20.dp)) else Modifier),
                    onClick = { onSelect(comp) },
                ) {
                    Row(Modifier.padding(FiskalSpacing.card), verticalAlignment = Alignment.CenterVertically) {
                        TvrtkaAvatar(comp.opis())
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(comp.opis(), style = MaterialTheme.typography.titleMedium, color = t.ink)
                                if (aktivna) { Spacer(Modifier.width(8.dp)); MiniBadge("AKTIVNA") }
                            }
                            Text(
                                "OIB ${comp.oib.ifBlank { "—" }} · ${comp.okolina.opis} · " +
                                    "${comp.djelatnosti.size} " + if (comp.djelatnosti.size == 1) "djelatnost" else "djelatnosti",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.muted,
                            )
                        }
                        Icon(
                            if (aktivna) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null, tint = if (aktivna) t.terracotta else t.mutedSoft,
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { onEdit(comp) }) { Text("Uredi") }
                    }
                }
            }
            item {
                DodajTvrtkuPloca(onAdd)
            }
        }
    }
}

@Composable
private fun TvrtkaAvatar(naziv: String) {
    val t = LocalFiskalTokens.current
    val inicijali = naziv.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" }
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(t.oliveTint),
        contentAlignment = Alignment.Center,
    ) { Text(inicijali, color = t.oliveTintInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun DodajTvrtkuPloca(onAdd: () -> Unit) {
    val t = LocalFiskalTokens.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, t.borderStrong, RoundedCornerShape(20.dp))
            .clickable(onClick = onAdd)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = t.olive)
            Spacer(Modifier.width(8.dp))
            Text("Dodaj novu tvrtku", color = t.olive, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
