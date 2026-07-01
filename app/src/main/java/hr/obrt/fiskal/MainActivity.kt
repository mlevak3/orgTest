package hr.obrt.fiskal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.obrt.fiskal.ui.ActivityPickerScreen
import hr.obrt.fiskal.ui.AppViewModel
import hr.obrt.fiskal.ui.ArticlesScreen
import hr.obrt.fiskal.ui.BackupScreen
import hr.obrt.fiskal.ui.CompanyListScreen
import hr.obrt.fiskal.ui.HistoryScreen
import hr.obrt.fiskal.ui.HomeScreen
import hr.obrt.fiskal.ui.InvoiceDetailScreen
import hr.obrt.fiskal.ui.InvoiceScreen
import hr.obrt.fiskal.ui.InvoiceSetupScreen
import hr.obrt.fiskal.ui.SettingsScreen
import hr.obrt.fiskal.ui.theme.FiskalTheme

private enum class Screen {
    CompanyList, Home, Settings, ActivityPicker, InvoiceSetup, Invoice, History, Detail, Articles, Backup
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FiskalTheme {
                val vm: AppViewModel = viewModel()
                var screen by remember {
                    mutableStateOf(if (vm.selected.value == null) Screen.CompanyList else Screen.Home)
                }

                // Sustavski "natrag" vodi na početnu (osim na početnoj/odabiru tvrtke;
                // Invoice ima vlastitu potvrdu izlaza).
                BackHandler(enabled = screen != Screen.Home && screen != Screen.CompanyList && screen != Screen.Invoice) {
                    screen = when (screen) {
                        Screen.Detail -> Screen.History
                        Screen.InvoiceSetup -> if (vm.trebaOdabirDjelatnosti()) Screen.ActivityPicker else Screen.Home
                        else -> Screen.Home
                    }
                }

                when (screen) {
                    Screen.CompanyList -> CompanyListScreen(
                        vm,
                        onSelect = { vm.selectCompany(it); screen = Screen.Home },
                        onAdd = { vm.newCompany(); screen = Screen.Settings },
                        onEdit = { vm.editCompany(it); screen = Screen.Settings },
                        onBack = if (vm.selected.value != null) ({ screen = Screen.Home }) else null,
                    )

                    Screen.Home -> HomeScreen(
                        vm,
                        onNewInvoice = {
                            vm.pripremiNoviRacun()
                            screen = if (vm.trebaOdabirDjelatnosti()) Screen.ActivityPicker else Screen.InvoiceSetup
                        },
                        onHistory = { vm.loadHistory(); screen = Screen.History },
                        onArticles = { vm.biranjeArtikla.value = false; vm.loadArticles(); screen = Screen.Articles },
                        onSettings = { vm.selected.value?.let { vm.editCompany(it) }; screen = Screen.Settings },
                        onCompanies = { screen = Screen.CompanyList },
                        onBackup = { screen = Screen.Backup },
                    )

                    Screen.Backup -> BackupScreen(onBack = { vm.refreshCompanies(); screen = Screen.Home })

                    Screen.Settings -> SettingsScreen(
                        vm,
                        onClose = { screen = if (vm.selected.value != null) Screen.Home else Screen.CompanyList },
                    )

                    Screen.ActivityPicker -> ActivityPickerScreen(
                        vm,
                        onPicked = { vm.odaberiDjelatnost(it); screen = Screen.InvoiceSetup },
                        onBack = { screen = Screen.Home },
                    )

                    Screen.InvoiceSetup -> InvoiceSetupScreen(
                        vm,
                        onChangeActivity = if (vm.trebaOdabirDjelatnosti()) ({ screen = Screen.ActivityPicker }) else null,
                        onContinue = { screen = Screen.Invoice },
                        onBack = { screen = Screen.Home },
                    )

                    Screen.Invoice -> InvoiceScreen(
                        vm,
                        onHome = { screen = Screen.Home },
                        onHistory = { vm.loadHistory(); screen = Screen.History },
                        onSettings = { vm.selected.value?.let { vm.editCompany(it) }; screen = Screen.Settings },
                        onArticles = { vm.biranjeArtikla.value = false; vm.loadArticles(); screen = Screen.Articles },
                        onPickArticle = { vm.biranjeArtikla.value = true; vm.loadArticles(); screen = Screen.Articles },
                    )

                    Screen.Articles -> ArticlesScreen(
                        vm,
                        onPick = if (vm.biranjeArtikla.value) {
                            { a -> vm.dodajIzArtikla(a); vm.biranjeArtikla.value = false; screen = Screen.Invoice }
                        } else null,
                        onBack = {
                            val pick = vm.biranjeArtikla.value
                            vm.biranjeArtikla.value = false
                            screen = if (pick) Screen.Invoice else Screen.Home
                        },
                    )

                    Screen.History -> HistoryScreen(
                        vm,
                        onOpen = { vm.openDetail(it); screen = Screen.Detail },
                        onBack = { screen = Screen.Home },
                    )

                    Screen.Detail -> InvoiceDetailScreen(
                        vm,
                        onBack = { vm.closeDetail(); screen = Screen.History },
                        onCopy = { vm.detail.value?.let { vm.kopirajURacun(it) }; screen = Screen.Invoice },
                    )
                }
            }
        }
    }
}
