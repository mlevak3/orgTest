package hr.obrt.fiskal.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hr.obrt.fiskal.data.Artikl
import hr.obrt.fiskal.data.ArticleStore
import hr.obrt.fiskal.data.CompanyStore
import hr.obrt.fiskal.data.Djelatnost
import hr.obrt.fiskal.data.InvoiceStore
import hr.obrt.fiskal.data.NaplatniUredaj
import hr.obrt.fiskal.data.PoslovniProstor
import hr.obrt.fiskal.data.SavedInvoice
import hr.obrt.fiskal.data.Tvrtka
import hr.obrt.fiskal.fiskal.CaStore
import hr.obrt.fiskal.fiskal.FiskalCertificate
import hr.obrt.fiskal.fiskal.FiskalIshod
import hr.obrt.fiskal.fiskal.FiskalRezultat
import hr.obrt.fiskal.fiskal.FiskalService
import hr.obrt.fiskal.fiskal.ReceiptData
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import hr.obrt.fiskal.model.Zaglavlje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import java.util.UUID

/**
 * Stavka u obrascu (tekstualni unos).
 * [jedCijena] je jedinična cijena bez PDV-a (za PDV obveznika) odnosno cijena
 * (za neobveznika). [neto], [pdvIznos] i [ukupno] su izračunati, ali ih korisnik
 * može ručno korigirati (zaokruživanje).
 */
data class StavkaInput(
    var naziv: String = "",
    var kolicina: String = "1",
    var jedMjere: String = "kom",
    var jedCijena: String = "",
    var pdvStopa: String = "25",
    var neto: String = "",
    var pdvIznos: String = "",
    var ukupno: String = "",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val companyStore = CompanyStore(app)
    private val invoiceStore = InvoiceStore(app)
    private val articleStore = ArticleStore(app)

    // --- Tvrtke ---
    val companies = mutableStateListOf<Tvrtka>()
    val selected = mutableStateOf<Tvrtka?>(null)
    val editing = mutableStateOf<Tvrtka?>(null)

    // --- Odabir djelatnosti / poslovnog prostora / naplatnog uređaja (za novi račun) ---
    val selectedDjelatnost = mutableStateOf<Djelatnost?>(null)
    val selectedProstor = mutableStateOf<PoslovniProstor?>(null)
    val selectedUredjaj = mutableStateOf<NaplatniUredaj?>(null)
    val brojRacuna = mutableStateOf("1")

    // --- Stanje računa ---
    val stavke = mutableStateListOf(StavkaInput())
    val nacinPlac = mutableStateOf(NacinPlac.G)
    /** Storno — svi iznosi računa idu u minus. */
    val storno = mutableStateOf(false)
    val kupacNaziv = mutableStateOf("")
    val kupacOib = mutableStateOf("")
    val napomena = mutableStateOf("")

    // --- Šifrarnik artikala ---
    val articles = mutableStateListOf<Artikl>()
    val editingArticle = mutableStateOf<Artikl?>(null)
    val biranjeArtikla = mutableStateOf(false)

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
        pripremiNoviRacun()
    }

    // --- Djelatnost / poslovni prostor / naplatni uređaj ---

    /** Treba li prikazati zaseban ekran odabira djelatnosti (>1 djelatnost). */
    fun trebaOdabirDjelatnosti(): Boolean = (selected.value?.djelatnosti?.size ?: 0) > 1

    /** Postavlja zadanu djelatnost tvrtke (i njome zadani prostor/uređaj/broj) te čisti obrazac. */
    fun pripremiNoviRacun() {
        val t = selected.value ?: return
        odaberiDjelatnost(t.zadanaDjelatnost())
        resetRacun()
    }

    fun odaberiDjelatnost(d: Djelatnost) {
        selectedDjelatnost.value = d
        odaberiProstor(d.zadaniProstor())
    }

    fun odaberiProstor(p: PoslovniProstor) {
        selectedProstor.value = p
        val d = selectedDjelatnost.value
        val zadani = d?.zadaniNaplatniUredjajId
        val u = p.naplatniUredjaji.firstOrNull { it.id == zadani } ?: p.naplatniUredjaji.first()
        odaberiUredjaj(u)
    }

    fun odaberiUredjaj(u: NaplatniUredaj) {
        selectedUredjaj.value = u
        azurirajPredlozeniBroj()
    }

    fun setBrojRacuna(v: String) { brojRacuna.value = v.filter(Char::isDigit) }

    private fun azurirajPredlozeniBroj() {
        val d = selectedDjelatnost.value ?: return
        val p = selectedProstor.value ?: return
        val u = selectedUredjaj.value ?: return
        brojRacuna.value = (if (d.oznSlijed == OznSlijed.P) p.sljedeciBroj else u.sljedeciBroj).toString()
    }

    /** Nakon uspješne fiskalizacije: ponovno učita tvrtku i postavi selekciju na osvježene objekte. */
    private fun osvjeziSelekcijuNakonSpremanja(tvrtkaId: String, djelatnostId: String, prostorId: String, uredjajId: String) {
        refreshCompanies()
        val t = companies.firstOrNull { it.id == tvrtkaId } ?: return
        val d = t.djelatnosti.firstOrNull { it.id == djelatnostId } ?: return
        val p = d.poslovniProstori.firstOrNull { it.id == prostorId } ?: return
        val u = p.naplatniUredjaji.firstOrNull { it.id == uredjajId } ?: return
        selectedDjelatnost.value = d
        selectedProstor.value = p
        selectedUredjaj.value = u
        azurirajPredlozeniBroj()
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
    fun dodajStavku() = stavke.add(
        StavkaInput(
            pdvStopa = selected.value?.zadanaPdvStopa ?: "25",
            jedMjere = selected.value?.zadanaJedMjere ?: "kom",
        )
    )
    fun ukloniStavku(index: Int) { if (stavke.size > 1) stavke.removeAt(index) }

    private fun uSustavuPdv(): Boolean = selected.value?.uSustavuPdv == true
    private fun fmt(b: BigDecimal): String = b.setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun setNaziv(i: Int, v: String) { stavke[i] = stavke[i].copy(naziv = v) }
    fun setKolicina(i: Int, v: String) { stavke[i] = preracunajBazu(stavke[i].copy(kolicina = v)) }
    fun setJedMjere(i: Int, v: String) { stavke[i] = stavke[i].copy(jedMjere = v) }
    fun setJedCijena(i: Int, v: String) { stavke[i] = preracunajBazu(stavke[i].copy(jedCijena = v)) }
    fun setStopa(i: Int, v: String) { stavke[i] = preracunajOdNeto(stavke[i].copy(pdvStopa = v)) }
    fun setNeto(i: Int, v: String) { stavke[i] = preracunajOdNeto(stavke[i].copy(neto = v)) }
    fun setPdvIznos(i: Int, v: String) {
        val s = stavke[i].copy(pdvIznos = v)
        stavke[i] = s.copy(ukupno = fmt(parse(s.neto).add(parse(s.pdvIznos))))
    }
    fun setUkupno(i: Int, v: String) { stavke[i] = stavke[i].copy(ukupno = v) }

    /** Iz količine i jedinične cijene izračuna neto, pa PDV i ukupno. */
    private fun preracunajBazu(s: StavkaInput): StavkaInput {
        val neto = parse(s.kolicina).multiply(parse(s.jedCijena)).setScale(2, RoundingMode.HALF_UP)
        return if (uSustavuPdv()) {
            val pdv = neto.multiply(parse(s.pdvStopa)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            s.copy(neto = fmt(neto), pdvIznos = fmt(pdv), ukupno = fmt(neto.add(pdv)))
        } else {
            s.copy(neto = fmt(neto), pdvIznos = "0.00", ukupno = fmt(neto))
        }
    }

    /** Iz neta i stope izračuna PDV i ukupno (neto je zadan). */
    private fun preracunajOdNeto(s: StavkaInput): StavkaInput {
        val neto = parse(s.neto)
        val pdv = neto.multiply(parse(s.pdvStopa)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        return s.copy(pdvIznos = fmt(pdv), ukupno = fmt(neto.add(pdv)))
    }

    fun ukupno(): BigDecimal = zbroj { it.ukupno }
    fun zbrojNeto(): BigDecimal = zbroj { it.neto }
    fun zbrojPdv(): BigDecimal = zbroj { it.pdvIznos }

    private inline fun zbroj(selector: (StavkaInput) -> String): BigDecimal {
        val base = stavke.fold(BigDecimal.ZERO) { acc, s -> acc.add(parse(selector(s))) }
            .setScale(2, RoundingMode.HALF_UP)
        return if (storno.value) base.negate() else base
    }

    /** Ima li obrazac nespremljenog unosa (za potvrdu izlaza). */
    fun imaUnos(): Boolean =
        stavke.any { it.naziv.isNotBlank() || parse(it.ukupno).signum() != 0 } ||
            kupacNaziv.value.isNotBlank() || kupacOib.value.isNotBlank() || napomena.value.isNotBlank()

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
                onSuccess = { ishod.value = it },
                onFailure = { greska.value = it.message ?: "Nepoznata greška." },
            )
            // Osvježi tvrtku/selekciju bez obzira na ishod (povecajBroj se poziva samo kod uspjeha,
            // ali refreshCompanies je bezopasan i inače).
            val t = selected.value
            val d = selectedDjelatnost.value
            val p = selectedProstor.value
            val u = selectedUredjaj.value
            if (t != null && d != null && p != null && u != null) {
                osvjeziSelekcijuNakonSpremanja(t.id, d.id, p.id, u.id)
            }
        }
    }

    private fun izvrsi(): Result<FiskalIshod> = runCatching {
        val t = selected.value ?: throw IllegalStateException("Nije odabrana tvrtka.")
        val djelatnost = selectedDjelatnost.value ?: throw IllegalStateException("Nije odabrana djelatnost.")
        val prostor = selectedProstor.value ?: throw IllegalStateException("Nije odabran poslovni prostor.")
        val uredjaj = selectedUredjaj.value ?: throw IllegalStateException("Nije odabran naplatni uređaj.")
        val broj = brojRacuna.value.toLongOrNull()
            ?: throw IllegalStateException("Broj računa mora biti cijeli broj.")

        val bytes = companyStore.certBytes(t.id)
            ?: throw IllegalStateException("Certifikat nije učitan (Postavke tvrtke).")
        val cert = try {
            FiskalCertificate.load(bytes.inputStream(), companyStore.lozinka(t.id).toCharArray())
        } catch (e: Exception) {
            throw IllegalStateException("Ne mogu otvoriti certifikat — provjeri lozinku certifikata u Postavkama. (${e.message})")
        }
        val caCerts = CaStore.loadExtraCas(getApplication(), companyStore.caBytes(t.id))

        val racun = Racun(
            zaglavlje = Zaglavlje(
                oib = t.oib,
                uSustavuPdv = t.uSustavuPdv,
                oznPosPr = prostor.oznaka,
                oznNapUr = uredjaj.oznaka,
                oznSlijed = djelatnost.oznSlijed,
                oibOper = t.oibOper.ifBlank { t.oib },
            ),
            brOznRac = broj,
            datVrijeme = Date(),
            stavke = stavke.map {
                fun iznos(x: String) = parse(x).let { v -> if (storno.value) v.negate() else v }
                Stavka(
                    naziv = it.naziv,
                    kolicina = parse(it.kolicina),
                    pdvStopa = parse(it.pdvStopa),
                    neto = iznos(it.neto),
                    pdvIznos = iznos(it.pdvIznos),
                    ukupno = iznos(it.ukupno),
                    jedMjere = it.jedMjere.ifBlank { "kom" },
                )
            },
            nacinPlac = nacinPlac.value,
        )

        val service = FiskalService(cert, t.okolina, t.ignoreTls, caCerts)
        val ishod = service.fiskaliziraj(racun)

        spremiUPovijest(t, ishod)
        if (ishod.rezultat is FiskalRezultat.Uspjeh) {
            companyStore.povecajBroj(t.id, djelatnost.id, prostor.id, uredjaj.id)
        }
        ishod
    }

    private fun spremiUPovijest(t: Tvrtka, ishod: FiskalIshod) {
        val status = when (val r = ishod.rezultat) {
            is FiskalRezultat.Uspjeh -> "Fiskaliziran"
            is FiskalRezultat.Greska -> "CIS greška: ${r.sifra} ${r.poruka}"
            is FiskalRezultat.Neizvjesno -> "NEIZVJESNO — provjeri (možda fiskalizirano)"
            is FiskalRezultat.Mreza -> "Nije poslano: ${r.poruka}"
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
                kupac = kupacNaziv.value,
                kupacOib = kupacOib.value,
                napomena = napomena.value,
            )
        )
    }

    private fun validiraj(): String? {
        val t = selected.value ?: return "Odaberi tvrtku."
        if (t.oib.length != 11) return "OIB tvrtke mora imati 11 znamenki (Postavke)."
        if (!companyStore.certPostoji(t.id)) return "FINA certifikat nije učitan (Postavke tvrtke)."
        if (companyStore.lozinka(t.id).isBlank()) return "Lozinka certifikata nije postavljena (Postavke)."
        if (selectedDjelatnost.value == null || selectedProstor.value == null || selectedUredjaj.value == null)
            return "Odaberi djelatnost, poslovni prostor i naplatni uređaj."
        if (brojRacuna.value.toLongOrNull() == null) return "Broj računa mora biti cijeli broj."
        if (stavke.none { it.naziv.isNotBlank() && parse(it.ukupno).signum() != 0 })
            return "Dodaj barem jednu stavku s iznosom (≠ 0)."
        return null
    }

    fun resetRacun() {
        val t = selected.value
        stavke.clear()
        stavke.add(StavkaInput(pdvStopa = t?.zadanaPdvStopa ?: "25", jedMjere = t?.zadanaJedMjere ?: "kom"))
        nacinPlac.value = t?.zadaniNacinPlac ?: NacinPlac.G
        storno.value = false
        kupacNaziv.value = ""
        kupacOib.value = ""
        napomena.value = ""
        ishod.value = null
        greska.value = null
    }

    // --- Šifrarnik artikala ---
    fun loadArticles() {
        val t = selected.value ?: return
        articles.clear()
        articles.addAll(articleStore.zaTvrtku(t.id))
    }

    fun newArticle() {
        val t = selected.value
        editingArticle.value = Artikl(
            jedMjere = t?.zadanaJedMjere ?: "kom",
            pdvStopa = if (t?.uSustavuPdv == true) (t.zadanaPdvStopa.toBigDecimalOrNull() ?: BigDecimal("25")) else BigDecimal.ZERO,
        )
    }

    fun editArticle(a: Artikl) { editingArticle.value = a }

    fun saveArticle(a: Artikl) {
        selected.value?.let { articleStore.spremi(it.id, a) }
        loadArticles()
        editingArticle.value = null
    }

    fun deleteArticle(a: Artikl) {
        selected.value?.let { articleStore.obrisi(it.id, a.id) }
        loadArticles()
        editingArticle.value = null
    }

    /** Dodaje stavku iz artikla (zamijeni prazan red ili dodaj novi). */
    fun dodajIzArtikla(a: Artikl) {
        val novo = preracunajBazu(
            StavkaInput(naziv = a.naziv, kolicina = "1", jedMjere = a.jedMjere, jedCijena = fmt(a.jedCijena), pdvStopa = fmt(a.pdvStopa))
        )
        val idx = stavke.indexOfFirst { it.naziv.isBlank() && parse(it.ukupno).signum() == 0 }
        if (idx >= 0) stavke[idx] = novo else stavke.add(novo)
    }

    /** Učita stavke i podatke postojećeg računa u novi obrazac (za ponovno izdavanje/ispravak). */
    fun kopirajURacun(si: SavedInvoice) {
        stavke.clear()
        si.racun.stavke.forEach { s ->
            val neto = s.neto.abs()
            val jed = if (s.kolicina.signum() != 0) neto.divide(s.kolicina, 2, RoundingMode.HALF_UP) else neto
            stavke.add(
                StavkaInput(
                    naziv = s.naziv,
                    kolicina = s.kolicina.toPlainString(),
                    jedMjere = s.jedMjere,
                    jedCijena = fmt(jed),
                    pdvStopa = fmt(s.pdvStopa),
                    neto = fmt(neto),
                    pdvIznos = fmt(s.pdvIznos.abs()),
                    ukupno = fmt(s.ukupno.abs()),
                )
            )
        }
        if (stavke.isEmpty()) stavke.add(StavkaInput())
        kupacNaziv.value = si.kupac
        kupacOib.value = si.kupacOib
        napomena.value = si.napomena
        storno.value = false
        nacinPlac.value = si.racun.nacinPlac
        ishod.value = null
        greska.value = null
        detail.value = null
    }

    // --- Povijest ---
    fun loadHistory() {
        val t = selected.value ?: return
        history.clear()
        history.addAll(invoiceStore.zaTvrtku(t.id))
    }

    /** Broj fiskaliziranih računa i promet za danas (odabrana tvrtka). Za prikaz na početnoj. */
    fun statistikaDanas(): Pair<Int, BigDecimal> {
        val t = selected.value ?: return 0 to BigDecimal.ZERO
        val danas = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val danasnji = invoiceStore.zaTvrtku(t.id).filter { it.jir != null && it.createdAt >= danas }
        val promet = danasnji.fold(BigDecimal.ZERO) { acc, si -> acc.add(si.racun.iznosUkupno) }
            .setScale(2, RoundingMode.HALF_UP)
        return danasnji.size to promet
    }

    /** Zadnji spremljeni račun odabrane tvrtke (najnoviji prvi), za brzu karticu na početnoj. */
    fun zadnjiRacun(): SavedInvoice? {
        val t = selected.value ?: return null
        return invoiceStore.zaTvrtku(t.id).firstOrNull()
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
        val t = selected.value
        return ReceiptData(
            naslovTvrtke = t?.opis() ?: "",
            racun = i.racun, jir = i.jir, zki = i.zki, qrUrl = i.qrUrl,
            kupac = kupacNaziv.value, kupacOib = kupacOib.value, napomena = napomena.value,
            logoPng = t?.let { companyStore.logoBytes(it.id) },
        )
    }

    fun receiptFromSaved(si: SavedInvoice) = ReceiptData(
        naslovTvrtke = si.naslovTvrtke,
        racun = si.racun,
        jir = si.jir,
        zki = si.zki,
        qrUrl = si.qrUrl,
        kupac = si.kupac,
        kupacOib = si.kupacOib,
        napomena = si.napomena,
        logoPng = companyStore.logoBytes(si.companyId),
    )

    /** MAC adresa Bluetooth pisača trenutno odabrane tvrtke (ako je postavljena). */
    fun printerAddress(): String? =
        selected.value?.printerAddress?.takeIf { it.isNotBlank() }

    fun printerAddressFor(companyId: String): String? =
        companies.firstOrNull { it.id == companyId }?.printerAddress?.takeIf { it.isNotBlank() }

    private fun parse(s: String): BigDecimal =
        s.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
}
