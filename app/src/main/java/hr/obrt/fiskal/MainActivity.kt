package hr.obrt.fiskal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.obrt.fiskal.ui.AppViewModel
import hr.obrt.fiskal.ui.InvoiceScreen
import hr.obrt.fiskal.ui.SettingsScreen
import hr.obrt.fiskal.ui.theme.FiskalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FiskalTheme {
                val vm: AppViewModel = viewModel()
                var prikaziPostavke by remember { mutableStateOf(false) }

                if (prikaziPostavke) {
                    SettingsScreen(vm, onBack = { prikaziPostavke = false })
                } else {
                    InvoiceScreen(vm, onSettings = { prikaziPostavke = true })
                }
            }
        }
    }
}
