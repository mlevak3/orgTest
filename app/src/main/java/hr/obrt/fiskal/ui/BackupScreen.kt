package hr.obrt.fiskal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.BackupManager
import hr.obrt.fiskal.ui.components.FiskalCard
import hr.obrt.fiskal.ui.components.FiskalOutlineButton
import hr.obrt.fiskal.ui.components.FiskalPrimaryButton
import hr.obrt.fiskal.ui.components.IconTile
import hr.obrt.fiskal.ui.components.LightHeader
import hr.obrt.fiskal.ui.theme.FiskalSpacing
import hr.obrt.fiskal.ui.theme.LocalFiskalTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val t = LocalFiskalTokens.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var poruka by remember { mutableStateOf<String?>(null) }
    var radi by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        radi = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = BackupManager.export(ctx)
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Ne mogu otvoriti datoteku za pisanje.")
                }
            }
            radi = false
            poruka = ok.fold({ "Sigurnosna kopija spremljena." }, { "Izvoz nije uspio: ${it.message}" })
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        radi = true
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val json = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                    BackupManager.import(ctx, json).getOrThrow()
                }
            }
            radi = false
            poruka = res.fold(
                { n -> "Uvezeno tvrtki: $n. Ponovno otvori Postavke/Tvrtke da vidiš promjene." },
                { "Uvoz nije uspio: ${it.message}" },
            )
        }
    }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        LightHeader("Sigurnosna kopija", "Izvoz i uvoz svih podataka", onBack = onBack)

        Column(
            Modifier.padding(horizontal = FiskalSpacing.screenX),
            verticalArrangement = Arrangement.spacedBy(FiskalSpacing.stackGap),
        ) {
            FiskalCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(FiskalSpacing.card), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.CloudUpload)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Izvezi ili uvezi sve podatke aplikacije: tvrtke, FINA certifikate, šifrarnik artikala i povijest računa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.ink,
                    )
                }
            }

            FiskalCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(FiskalSpacing.card).background(t.errorBg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = t.error, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Datoteka sadrži osjetljive podatke — privatni certifikat i njegovu lozinku. Čuvaj je sigurno i ne dijeli s nepoznatima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.error,
                    )
                }
            }

            FiskalPrimaryButton(
                "Izvezi sigurnosnu kopiju",
                enabled = !radi,
                onClick = {
                    val ime = "fiskal-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.json"
                    exportLauncher.launch(ime)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FiskalOutlineButton(
                "Uvezi sigurnosnu kopiju",
                enabled = !radi,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (radi) LinearProgressIndicator(Modifier.fillMaxWidth())
            poruka?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        }
    }
}
