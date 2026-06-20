package hr.obrt.fiskal.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.data.CompanyStore
import hr.obrt.fiskal.data.InvoiceStore
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.data.Tvrtka
import hr.obrt.fiskal.fiskal.CaStore
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalIshod
import hr.obrt.fiskal.fiskal.FiskalRezultat
import hr.obrt.fiskal.fiskal.FiskalService
import hr.obrt.fiskal.fiskal.ReceiptData
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.UUID

/** Stavka u obrascu (tekstualni unos). */
data class StavkaInput(
    var naziv: String = "",
    var kolicina: String = "1",
    var cijena: String = "",
    var pdvStopa: String = "25",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val companyStore = CompanyStore(app)
    private val invoiceStore = InvoiceStore(app)

    // --- Tvrtke ---
    val companies = mutableStateListOf<Tvrtka>()
    val selected = mutableStateOf<Tvrtka?>(null)
    val editing = mutableStateOf<Tvrtka?>(null)

    // --- Stanje računa ---
    val stavke = mutableStateListOf(StavkaInput())
    val nacinPlac = mutableStateOf(NacinPlac.G)

    // --- Izvršavanje / rezultat ---
    val ucitavanje = mutableStateOf(false)
    val ishod = mutableStateOf<FiskalIshod?>(null)
    val greska = mutableStateOf<String?>(null)

    // --- Povijest ---
    val history = mutableStateListOf<SavedInvoice>()
    val detail = mutableStateOf<SavedInvoice?>(null)

    init {
        refreshCompanies()
    }

    fun refreshCompanies() {
        companies.clear()
        companies.addAll(companyStore.sve())
        selected.value = companyStore.odabrana()
    }

    fun selectCompany(t: Tvrtka) {
        companyStore.odabranaId = t.id
        selected.value = t
        resetRacun()
    }

    fun newCompany() { editing.value = Tvrtka() }
    fun editCompany(t: Tvrtka) { editing.value = t }

    fun saveCompany(t: Tvrtka) {
        companyStore.spremiTvrtku(t)
        refreshCompanies()
        if (selected.value?.id == t.id) selected.value = t
        editing.value = null
    }

    fun deleteCompany(t: Tvrtka) {
        companyStore.obrisiTvrtku(t.id)
        refreshCompanies()
    }

    // --- Stavke ---
    fun dodajStavku() = stavke.add(StavkaInput())
    fun ukloniStavku(index: Int) { if (stavke.size > 1) stavke.removeAt(index) }
    fun azurirajStavku(index: Int, novo: StavkaInput) { stavke[index] = novo }

    fun ukupno(): BigDecimal =
        try {
            stavke.fold(BigDecimal.ZERO) { acc, s -> acc.add(parse(s.kolicina).multiply(parse(s.cijena))) }
                .setScale(2, RoundingMode.HALF_UP)
        } catch (_: Exception) {
            BigDecimal.ZERO
        }

    fun fiskaliziraj() {
        greska.value = null
        ishod.value = null

        val validacija = validiraj()
        if (validacija != null) { greska.value = validacija; return }

        ucitavanje.value = true
        viewModelScope.launch {
            val rezultat = withContext(Dispatchers.IO) { izvrsi() }
            ucitavanje.value = false
            rezultat.fold(
                onSuccess = { ishod.value = it; refreshCompanies() },
                onFailure = { greska.value = it.message ?: "Nepoznata greška." },
            )
        }
    }

    private fun izvrsi(): Result<FiskalIshod> = runCatching {
        val t = selected.value ?: throw IllegalStateException("Nije odabrana tvrtka.")
        val bytes = companyStore.certBytes(t.id)
            ?: throw IllegalStateException("Certifikat nije učitan (Postavke tvrtke).")
        val cert = FiskalCertificate.load(bytes.inputStream(), companyStore.lozinka(t.id).toCharArray())
        val caCerts = CaStore.loadExtraCas(getApplication(), companyStore.caBytes(t.id))

        val racun = Racun(
            zaglavlje = t.zaglavlje(),
            brOznRac = t.sljedeciBroj,
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

        val service = FiskalService(cert, t.okolina, t.ignoreTls, caCerts)
        val ishod = service.fiskaliziraj(racun)

        spremiUPovijest(t, ishod)
        if (ishod.rezultat is FiskalRezultat.Uspjeh) {
            companyStore.povecajBroj(t.id)
        }
        ishod
    }

    private fun spremiUPovijest(t: Tvrtka, ishod: FiskalIshod) {
        val status = when (val r = ishod.rezultat) {
            is FiskalRezultat.Uspjeh -> "Fiskaliziran"
            is FiskalRezultat.Greska -> "CIS greška: ${r.sifra} ${r.poruka}"
            is FiskalRezultat.Iznimka -> "Nije poslano: ${r.poruka}"
        }
        invoiceStore.spremi(
            SavedInvoice(
                id = UUID.randomUUID().toString(),
                companyId = t.id,
                naslovTvrtke = t.opis(),
                racun = ishod.racun,
                jir = ishod.jir,
                zki = ishod.zki,
                qrUrl = ishod.qrUrl,
                status = status,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    private fun validiraj(): String? {
        val t = selected.value ?: return "Odaberi tvrtku."
        if (t.oib.length != 11) return "OIB tvrtke mora imati 11 znamenki (Postavke)."
        if (!companyStore.certPostoji(t.id)) return "FINA certifikat nije učitan (Postavke tvrtke)."
        if (companyStore.lozinka(t.id).isBlank()) return "Lozinka certifikata nije postavljena (Postavke)."
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

    // --- Povijest ---
    fun loadHistory() {
        val t = selected.value ?: return
        history.clear()
        history.addAll(invoiceStore.zaTvrtku(t.id))
    }

    fun openDetail(si: SavedInvoice) { detail.value = si }
    fun closeDetail() { detail.value = null }

    fun obrisiIzPovijesti(si: SavedInvoice) {
        invoiceStore.obrisi(si)
        loadHistory()
    }

    // --- Ispis / email ---
    fun receiptFromIshod(): ReceiptData? {
        val i = ishod.value ?: return null
        val naziv = selected.value?.opis() ?: ""
        return ReceiptData.fromIshod(naziv, i)
    }

    fun receiptFromSaved(si: SavedInvoice) = ReceiptData(
        naslovTvrtke = si.naslovTvrtke,
        racun = si.racun,
        jir = si.jir,
        zki = si.zki,
        qrUrl = si.qrUrl,
    )

    private fun parse(s: String): BigDecimal =
        s.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
}
