package hr.obrt.fiskal.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.data.SettingsRepository
import hr.obrt.fiskal.fiskal.CaStore
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalIshod
import hr.obrt.fiskal.fiskal.FiskalRezultat
import hr.obrt.fiskal.fiskal.FiskalService
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Date

/** Stavka u obrascu (tekstualni unos). */
data class StavkaInput(
    var naziv: String = "",
    var kolicina: String = "1",
    var cijena: String = "",
    var pdvStopa: String = "25",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = SettingsRepository(app)

    // --- Stanje računa ---
    val stavke = mutableStateListOf(StavkaInput())
    val nacinPlac = mutableStateOf(NacinPlac.G)

    // --- Stanje izvršavanja ---
    val ucitavanje = mutableStateOf(false)
    val ishod = mutableStateOf<FiskalIshod?>(null)
    val greska = mutableStateOf<String?>(null)

    fun dodajStavku() = stavke.add(StavkaInput())
    fun ukloniStavku(index: Int) {
        if (stavke.size > 1) stavke.removeAt(index)
    }

    fun azurirajStavku(index: Int, novo: StavkaInput) {
        stavke[index] = novo
    }

    fun ukupno(): BigDecimal =
        try {
            stavke.fold(BigDecimal.ZERO) { acc, s ->
                acc.add(parse(s.kolicina).multiply(parse(s.cijena)))
            }.setScale(2, java.math.RoundingMode.HALF_UP)
        } catch (_: Exception) {
            BigDecimal.ZERO
        }

    /** Pokreće fiskalizaciju trenutnog računa. */
    fun fiskaliziraj() {
        greska.value = null
        ishod.value = null

        val validacija = validiraj()
        if (validacija != null) {
            greska.value = validacija
            return
        }

        ucitavanje.value = true
        viewModelScope.launch {
            val rezultat = withContext(Dispatchers.IO) { izvrsi() }
            ucitavanje.value = false
            rezultat.fold(
                onSuccess = { ishod.value = it },
                onFailure = { greska.value = it.message ?: "Nepoznata greška." },
            )
        }
    }

    private fun izvrsi(): Result<FiskalIshod> = runCatching {
        val bytes = repo.certifikatBytes()
            ?: throw IllegalStateException("Certifikat nije učitan. Otvori Postavke.")
        val cert = FiskalCertificate.load(bytes.inputStream(), repo.certPassword.toCharArray())
        val caCerts = CaStore.loadExtraCas(getApplication(), repo.caBytes())

        val racun = Racun(
            zaglavlje = repo.zaglavlje,
            brOznRac = repo.sljedeciBroj,
            datVrijeme = Date(),
            stavke = stavke.map {
                Stavka(
                    naziv = it.naziv,
                    kolicina = parse(it.kolicina),
                    jedinicnaCijena = parse(it.cijena),
                    pdvStopa = parse(it.pdvStopa),
                )
            },
            nacinPlac = nacinPlac.value,
        )

        val service = FiskalService(cert, repo.okolina, repo.ignoreTlsTrust, caCerts)
        val ishod = service.fiskaliziraj(racun)

        // Broj računa povećavamo tek kad je CIS prihvatio (vratio JIR).
        if (ishod.rezultat is FiskalRezultat.Uspjeh) {
            repo.sljedeciBroj = repo.sljedeciBroj + 1
        }
        ishod
    }

    private fun validiraj(): String? {
        if (repo.oib.length != 11) return "OIB obveznika mora imati 11 znamenki (Postavke)."
        if (!repo.certifikatPostoji()) return "FINA certifikat nije učitan (Postavke)."
        if (repo.certPassword.isBlank()) return "Lozinka certifikata nije postavljena (Postavke)."
        if (stavke.none { it.naziv.isNotBlank() && parse(it.cijena) > BigDecimal.ZERO })
            return "Dodaj barem jednu stavku s cijenom."
        return null
    }

    fun resetRacun() {
        stavke.clear()
        stavke.add(StavkaInput())
        nacinPlac.value = NacinPlac.G
        ishod.value = null
        greska.value = null
    }

    private fun parse(s: String): BigDecimal =
        s.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
}
