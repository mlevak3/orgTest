package hr.obrt.fiskal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hr.obrt.fiskal.data.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
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

    Scaffold(
        topBar = {
            val cs = MaterialTheme.colorScheme
            TopAppBar(
                title = { Text("Sigurnosna kopija") },
                navigationIcon = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = cs.onPrimary)) { Text("Početna") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.primary, titleContentColor = cs.onPrimary),
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Izvezi ili uvezi sve podatke aplikacije: tvrtke, FINA certifikate, " +
                    "šifrarnik artikala i povijest računa.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Datoteka sadrži OSJETLJIVE podatke — privatni certifikat i njegovu lozinku. " +
                        "Čuvaj je sigurno (ne šalji nezaštićeno, ne dijeli s nepoznatima).",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                enabled = !radi,
                onClick = {
                    val ime = "fiskal-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.json"
                    exportLauncher.launch(ime)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Izvezi sigurnosnu kopiju") }
            OutlinedButton(
                enabled = !radi,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Uvezi sigurnosnu kopiju") }

            if (radi) LinearProgressIndicator(Modifier.fillMaxWidth())
            poruka?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
