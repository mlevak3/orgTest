package hr.obrt.fiskal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import hr.obrt.fiskal.data.Djelatnost
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.components.MiniBadge
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

/** Odabir djelatnosti prije izrade novog računa (prikazuje se samo ako tvrtka ima više njih). */
@Composable
fun ActivityPickerScreen(vm: AppViewModel, onPicked: (Djelatnost) -> Unit, onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    val tvrtka = vm.selected.value

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Odaberi djelatnost", onBack = onBack)

        LazyColumn(
            Modifier.padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
            contentPadding = PaddingValues(bottom = FiskalSpacing.listPad),
        ) {
            items(tvrtka?.djelatnosti ?: emptyList()) { d ->
                val zadana = tvrtka?.zadanaDjelatnostId == d.id
                FiskalCard(Modifier.fillMaxWidth(), onClick = { onPicked(d) }) {
                    Row(
                        Modifier.padding(FiskalSpacing.card).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.opis(), style = MaterialTheme.typography.titleMedium, color = t.ink)
                            Text(
                                "${d.poslovniProstori.size} posl. prostor(a) · " +
                                    "zadano: ${d.zadaniProstor().oznaka}/${d.zadaniUredjaj().oznaka}",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.muted,
                            )
                        }
                        if (zadana) MiniBadge("ZADANO")
                    }
                }
            }
        }
    }
}
