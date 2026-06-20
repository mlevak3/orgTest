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
import hr.obrt.fiskal.ui.CompanyListScreen
import hr.obrt.fiskal.ui.HistoryScreen
import hr.obrt.fiskal.ui.InvoiceDetailScreen
import hr.obrt.fiskal.ui.InvoiceScreen
import hr.obrt.fiskal.ui.SettingsScreen
import hr.obrt.fiskal.ui.theme.FiskalTheme

private enum class Screen { CompanyList, Settings, Invoice, History, Detail }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FiskalTheme {
                val vm: AppViewModel = viewModel()
                var screen by remember {
                    mutableStateOf(if (vm.selected.value == null) Screen.CompanyList else Screen.Invoice)
                }

                when (screen) {
                    Screen.CompanyList -> CompanyListScreen(
                        vm,
                        onSelect = { vm.selectCompany(it); screen = Screen.Invoice },
                        onAdd = { vm.newCompany(); screen = Screen.Settings },
                        onEdit = { vm.editCompany(it); screen = Screen.Settings },
                        onBack = if (vm.selected.value != null) ({ screen = Screen.Invoice }) else null,
                    )

                    Screen.Settings -> SettingsScreen(
                        vm,
                        onClose = { screen = if (vm.selected.value != null) Screen.Invoice else Screen.CompanyList },
                    )

                    Screen.Invoice -> InvoiceScreen(
                        vm,
                        onCompanies = { screen = Screen.CompanyList },
                        onHistory = { vm.loadHistory(); screen = Screen.History },
                        onSettings = { vm.selected.value?.let { vm.editCompany(it) }; screen = Screen.Settings },
                    )

                    Screen.History -> HistoryScreen(
                        vm,
                        onOpen = { vm.openDetail(it); screen = Screen.Detail },
                        onBack = { screen = Screen.Invoice },
                    )

                    Screen.Detail -> InvoiceDetailScreen(
                        vm,
                        onBack = { vm.closeDetail(); screen = Screen.History },
                    )
                }
            }
        }
    }
}
