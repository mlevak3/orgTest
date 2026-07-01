package hr.obrt.fiskal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.ui.theme.FiskalRadius
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens

/** Primarni gumb — maslina, pill, puna širina. BRAND-UPUTE 8.1. */
@Composable
fun FiskalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val t = LocalFiskalTokens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(FiskalRadius.pill),
        colors = ButtonDefaults.buttonColors(containerColor = t.olive, contentColor = t.oliveInk),
    ) {
        icon?.invoke()
        Text(text, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
    }
}

/** Terakota varijanta — ISKLJUČIVO za glavnu akciju pogleda (Naplati, FAB, Novi artikl/partner). */
@Composable
fun FiskalTerracottaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    val t = LocalFiskalTokens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .shadow(10.dp, RoundedCornerShape(FiskalRadius.pill), spotColor = t.terracotta.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(FiskalRadius.pill),
        colors = ButtonDefaults.buttonColors(containerColor = t.terracotta, contentColor = t.terracottaInk),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
        trailingIcon?.let { Spacer(Modifier.width(6.dp)); Icon(it, null, Modifier.size(18.dp)) }
    }
}

/** Sekundarni (outline) gumb — pozadina surface, rub borderStrong, tekst olive. */
@Composable
fun FiskalOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destruktivno: Boolean = false,
    icon: ImageVector? = null,
) {
    val t = LocalFiskalTokens.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(FiskalRadius.pill),
        border = BorderStroke(1.5.dp, if (destruktivno) t.errorBorder else t.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destruktivno) t.error else t.olive),
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
    }
}

/** Status pill — "Fiskaliziran" (zeleno) / "Nije fiskaliziran" (crveno). BRAND-UPUTE 8.4. */
@Composable
fun StatusBadge(fiskaliziran: Boolean, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    Box(
        modifier
            .clip(RoundedCornerShape(FiskalRadius.pill))
            .background(if (fiskaliziran) t.successBg else t.errorBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            if (fiskaliziran) "Fiskaliziran" else "Nije fiskaliziran",
            color = if (fiskaliziran) t.success else t.error,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Mali badge (npr. "AKTIVNA"/"DEMO"). */
@Composable
fun MiniBadge(text: String, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    Box(
        modifier
            .clip(RoundedCornerShape(FiskalRadius.pill))
            .background(t.terracottaTint)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = t.terracottaTintInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodySmall)
    }
}

/** Chip/segment — aktivan maslina pill, neaktivan surface+border. BRAND-UPUTE 8.3. */
@Composable
fun FiskalChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    Box(
        modifier
            .clip(RoundedCornerShape(FiskalRadius.pill))
            .background(if (selected) t.olive else t.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, t.border, RoundedCornerShape(FiskalRadius.pill)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) t.oliveInk else t.muted,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Ikona-pločica u kartici/gridu — kvadrat s tint pozadinom. BRAND-UPUTE 8.6. */
@Composable
fun IconTile(icon: ImageVector, terakota: Boolean = false, size: Dp = 44.dp) {
    val t = LocalFiskalTokens.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(FiskalRadius.tile))
            .background(if (terakota) t.terracottaTint else t.oliveTint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (terakota) t.terracottaTintInk else t.oliveTintInk, modifier = Modifier.size(size * 0.5f))
    }
}

/** Search pill — BRAND-UPUTE 8.8. */
@Composable
fun SearchPill(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().height(50.dp),
        placeholder = { Text(placeholder, color = t.placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = t.mutedSoft) },
        singleLine = true,
        shape = RoundedCornerShape(FiskalRadius.pill),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = t.surface, unfocusedContainerColor = t.surface,
            focusedBorderColor = t.olive, unfocusedBorderColor = t.border,
        ),
    )
}

/** Stepper količine — dva okrugla gumba (remove/add) s brojem između. BRAND-UPUTE 8.11. */
@Composable
fun QuantityStepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalFiskalTokens.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperGumb(Icons.Rounded.Remove, onMinus, t.oliveTint, t.oliveTintInk)
        Text(value, style = MaterialTheme.typography.titleSmall, color = t.ink, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
        StepperGumb(Icons.Rounded.Add, onPlus, t.oliveTint, t.oliveTintInk)
    }
}

@Composable
private fun StepperGumb(icon: ImageVector, onClick: () -> Unit, bg: Color, fg: Color) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)) }
}

/** Prazno stanje — ikona-pločica + naslov + opis + opcionalni CTA. BRAND-UPUTE 8.13. */
@Composable
fun FiskalEmptyState(
    icon: ImageVector,
    naslov: String,
    opis: String,
    modifier: Modifier = Modifier,
    akcija: (@Composable () -> Unit)? = null,
) {
    val t = LocalFiskalTokens.current
    Box(modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconTile(icon, size = 52.dp)
            Spacer(Modifier.height(4.dp))
            Text(naslov, style = MaterialTheme.typography.titleMedium, color = t.ink)
            Text(opis, style = MaterialTheme.typography.bodySmall, color = t.muted, textAlign = TextAlign.Center)
            akcija?.let { Spacer(Modifier.height(8.dp)); it() }
        }
    }
}

/** Lagani header pod-ekrana — okrugli natrag gumb + naziv (+ podnaslov) + opcionalna akcija. BRAND-UPUTE 8.15. */
@Composable
fun LightHeader(
    naslov: String,
    podnaslov: String? = null,
    onBack: (() -> Unit)? = null,
    akcija: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalFiskalTokens.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = FiskalSpacing.screenX, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(t.surface)
                    .border(1.dp, t.border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ArrowBack, "Natrag", tint = t.ink, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(naslov, style = MaterialTheme.typography.titleLarge, color = t.ink)
            podnaslov?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.muted) }
        }
        akcija?.invoke()
    }
}

/** Kartica prema brand tokenima (surface, radius.card, meka sjena). BRAND-UPUTE 8.5. */
@Composable
fun FiskalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalFiskalTokens.current
    val shape = RoundedCornerShape(FiskalRadius.card)
    val base = modifier.shadow(6.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
    if (onClick != null) {
        Card(onClick = onClick, modifier = base, shape = shape, colors = CardDefaults.cardColors(containerColor = t.surface)) {
            Column(content = content)
        }
    } else {
        Card(modifier = base, shape = shape, colors = CardDefaults.cardColors(containerColor = t.surface)) {
            Column(content = content)
        }
    }
}
