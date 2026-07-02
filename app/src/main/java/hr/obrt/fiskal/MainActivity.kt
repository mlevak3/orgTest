package hr.obrt.fiskal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import hr.obrt.fiskal.ui.ActivityPickerScreen
import hr.obrt.fiskal.ui.AppViewModel
import hr.obrt.fiskal.ui.ArticlesScreen
import hr.obrt.fiskal.ui.CompanyListScreen
import hr.obrt.fiskal.ui.HistoryScreen
import hr.obrt.fiskal.ui.HomeScreen
import hr.obrt.fiskal.ui.InvoiceDetailScreen
import hr.obrt.fiskal.ui.InvoiceScreen
import hr.obrt.fiskal.ui.InvoiceSetupScreen
import hr.obrt.fiskal.ui.PartnersScreen
import hr.obrt.fiskal.ui.ReportsScreen
import hr.obrt.fiskal.ui.SettingsScreen
import hr.obrt.fiskal.ui.components.FiskalBottomNav
import hr.obrt.fiskal.ui.components.NavTab
import hr.obrt.fiskal.ui.theme.FiskalTheme
import hr.obrt.fiskal.data.AppPreferences

private enum class Screen {
    CompanyList, Home, Settings, ActivityPicker, InvoiceSetup, Invoice, History, Detail, Articles, Partners, Reports
}

private val BOTTOM_NAV_SCREENS = setOf(Screen.Home, Screen.History, Screen.Articles, Screen.Partners, Screen.Reports)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appPrefs = remember { AppPreferences(this) }
            var tema by remember { mutableStateOf(appPrefs.tema) }
            FiskalTheme(tema = tema) {
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

                val onNewInvoice = {
                    vm.pripremiNoviRacun()
                    screen = when {
                        vm.trebaOdabirDjelatnosti() -> Screen.ActivityPicker
                        vm.trebaPostavkeRacuna() -> Screen.InvoiceSetup
                        // Jedna djelatnost s jednim prostorom i uređajem — ravno na unos stavki.
                        else -> Screen.Invoice
                    }
                }

                Box(Modifier.fillMaxSize()) {
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
                        tema = tema,
                        onToggleTema = { tema = tema.sljedeca(); appPrefs.tema = tema },
                        onHistory = { vm.loadHistory(); screen = Screen.History },
                        onSettings = { vm.selected.value?.let { vm.editCompany(it) }; screen = Screen.Settings },
                        onCompanies = { screen = Screen.CompanyList },
                        onOpenInvoice = { vm.loadHistory(); vm.openDetail(it); screen = Screen.Detail },
                    )

                    Screen.Reports -> ReportsScreen(vm, onBack = { screen = Screen.Home })

                    Screen.Settings -> SettingsScreen(
                        vm,
                        onClose = { screen = if (vm.selected.value != null) Screen.Home else Screen.CompanyList },
                    )

                    Screen.ActivityPicker -> ActivityPickerScreen(
                        vm,
                        onPicked = {
                            vm.odaberiDjelatnost(it)
                            screen = if (vm.trebaPostavkeRacuna()) Screen.InvoiceSetup else Screen.Invoice
                        },
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
                        onPickPartner = { vm.biranjePartnera.value = true; vm.loadPartners(); screen = Screen.Partners },
                    )

                    Screen.Articles -> ArticlesScreen(
                        vm,
                        onPick = if (vm.biranjeArtikla.value) {
                            { a -> vm.zapocniDodavanjeIzArtikla(a); vm.biranjeArtikla.value = false; screen = Screen.Invoice }
                        } else null,
                        onBack = {
                            val pick = vm.biranjeArtikla.value
                            vm.biranjeArtikla.value = false
                            screen = if (pick) Screen.Invoice else Screen.Home
                        },
                    )

                    Screen.Partners -> PartnersScreen(
                        vm,
                        onPick = if (vm.biranjePartnera.value) {
                            { p -> vm.odaberiPartnera(p); vm.biranjePartnera.value = false; screen = Screen.Invoice }
                        } else null,
                        onBack = {
                            val pick = vm.biranjePartnera.value
                            vm.biranjePartnera.value = false
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
                        onStorno = { vm.detail.value?.let { vm.stornirajRacun(it) }; screen = Screen.Invoice },
                    )
                }

                // Nav se ne prikazuje dok se s računa bira artikl/partner — tada su
                // Articles/Partners ekrani u "picker" modu i vode natrag na račun.
                val birackiMod = (screen == Screen.Articles && vm.biranjeArtikla.value) ||
                    (screen == Screen.Partners && vm.biranjePartnera.value)
                if (screen in BOTTOM_NAV_SCREENS && !birackiMod) {
                    FiskalBottomNav(
                        current = when (screen) {
                            Screen.History -> NavTab.RACUNI
                            Screen.Articles -> NavTab.ARTIKLI
                            Screen.Partners -> NavTab.PARTNERI
                            Screen.Reports -> NavTab.IZVJESTAJI
                            else -> NavTab.POCETNA
                        },
                        onPocetna = { screen = Screen.Home },
                        onRacuni = { vm.loadHistory(); screen = Screen.History },
                        onArtikli = { vm.biranjeArtikla.value = false; vm.loadArticles(); screen = Screen.Articles },
                        onPartneri = { vm.biranjePartnera.value = false; vm.loadPartners(); screen = Screen.Partners },
                        onIzvjestaji = { screen = Screen.Reports },
                        onNoviRacun = onNewInvoice,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                }
            }
        }
    }
}
