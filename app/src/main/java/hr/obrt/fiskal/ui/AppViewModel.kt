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
import hr.obrt.fiskal.data.Partner
import hr.obrt.fiskal.data.PartnerStore
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
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Potvrđena stavka računa (uvijek nastaje iz artikla u šifrarniku — nema
 * slobodnog unosa). [neto]/[pdvIznos]/[ukupno] su izvedeni iz količine i
 * jedinične cijene u trenutku potvrde.
 */
data class StavkaInput(
    var naziv: String = "",
    var kolicina: String = "1",
    var jedMjere: String = "kom",
    var jedCijena: String = "",
    var pdvStopa: String = "25",
    /** Popust na stavku, u postotku (0 = bez popusta). */
    var popust: String = "0",
    var neto: String = "",
    var pdvIznos: String = "",
    var ukupno: String = "",
)

/**
 * Stavka u tijeku unosa (dijalog za dodavanje/uređivanje) — nastaje odabirom
 * artikla iz šifrarnika. Naziv, jedinica mjere i PDV stopa su zaključani
 * (dolaze iz artikla); mijenjati se mogu samo količina i cijena.
 */
data class StavkaUnos(
    val naziv: String,
    val jedMjere: String,
    val pdvStopa: String,
    var kolicina: String = "1",
    var jedCijena: String = "",
    /** Popust na stavku, u postotku (0 = bez popusta). */
    var popust: String = "0",
)

/** Period za koji se generira izvještaj. */
enum class PeriodIzvjestaja(val naziv: String) {
    DANAS("Danas"), TJEDAN("7 dana"), MJESEC("Mjesec"), SVE("Sve"), PRILAGODJENO("Prilagođeno");

    /** Početak perioda (epoch millis), ili null za "Sve"/"Prilagođeno" (potonji ima vlastite granice). */
    fun pocetak(): Long? = when (this) {
        DANAS -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        TJEDAN -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        MJESEC -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        SVE, PRILAGODJENO -> null
    }
}

data class StavkaNacinPlac(val nacin: NacinPlac, val brojRacuna: Int, val ukupno: BigDecimal)
data class StavkaPdv(val stopa: String, val osnovica: BigDecimal, val pdv: BigDecimal, val ukupno: BigDecimal)
data class StavkaArtikl(val naziv: String, val kolicina: BigDecimal, val ukupno: BigDecimal, val jedMjere: String = "kom")

data class Izvjestaj(
    val brojRacuna: Int,
    val ukupanPromet: BigDecimal,
    val poNacinuPlac: List<StavkaNacinPlac>,
    val pdvRekapitulacija: List<StavkaPdv>,
    val poArtiklima: List<StavkaArtikl>,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val companyStore = CompanyStore(app)
    private val invoiceStore = InvoiceStore(app)
    private val articleStore = ArticleStore(app)
    private val partnerStore = PartnerStore(app)

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
    val stavke = mutableStateListOf<StavkaInput>()
    /** Ne-null dok je otvoren dijalog za dodavanje/uređivanje stavke. */
    val stavkaUnos = mutableStateOf<StavkaUnos?>(null)
    /** Indeks stavke koja se uređuje (null = dodaje se nova). */
    val uredjivanjeIndex = mutableStateOf<Int?>(null)
    val nacinPlac = mutableStateOf(NacinPlac.G)
    /** Popust na razini cijelog računa, u postotku (0 = bez popusta). */
    val popustRacuna = mutableStateOf("0")
    val kupacNaziv = mutableStateOf("")
    val kupacOib = mutableStateOf("")
    val kupacAdresa = mutableStateOf("")
    val napomena = mutableStateOf("")

    // --- Šifrarnik artikala ---
    val articles = mutableStateListOf<Artikl>()
    val editingArticle = mutableStateOf<Artikl?>(null)
    val biranjeArtikla = mutableStateOf(false)

    // --- Šifrarnik partnera ---
    val partners = mutableStateListOf<Partner>()
    val editingPartner = mutableStateOf<Partner?>(null)
    val biranjePartnera = mutableStateOf(false)

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

    /**
     * Treba li ekran potvrde prostora/uređaja/broja prije unosa stavki: preskače
     * se kad odabrana djelatnost ima točno jedan poslovni prostor s jednim
     * naplatnim uređajem (nema se što birati; predloženi broj se ionako vidi
     * i može promijeniti tek u iznimnim situacijama).
     */
    fun trebaPostavkeRacuna(): Boolean {
        val d = selectedDjelatnost.value ?: return true
        if (d.poslovniProstori.size > 1) return true
        return d.poslovniProstori.first().naplatniUredjaji.size > 1
    }

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

    /**
     * Predloženi broj računa uvijek se čita izravno iz spremljenog stanja (a ne
     * iz eventualno zastarjelih referenci u memoriji — npr. nakon što je
     * "Postavke tvrtke" spremljeno preko starije kopije), kako broj uvijek
     * odgovara stvarnom sljedećem broju prema postavkama slijednosti.
     */
    private fun azurirajPredlozeniBroj() {
        val t = selected.value ?: return
        val d = selectedDjelatnost.value ?: return
        val p = selectedProstor.value ?: return
        val u = selectedUredjaj.value ?: return
        val svjezaTvrtka = companyStore.sve().firstOrNull { it.id == t.id } ?: t
        val svjezaDjelatnost = svjezaTvrtka.djelatnosti.firstOrNull { it.id == d.id } ?: d
        val svjeziProstor = svjezaDjelatnost.poslovniProstori.firstOrNull { it.id == p.id } ?: p
        val svjeziUredjaj = svjeziProstor.naplatniUredjaji.firstOrNull { it.id == u.id } ?: u
        brojRacuna.value =
            (if (svjezaDjelatnost.oznSlijed == OznSlijed.P) svjeziProstor.sljedeciBroj else svjeziUredjaj.sljedeciBroj).toString()
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

    // --- Stavke (uvijek iz šifrarnika artikala; dodaju se/uređuju jedna po jedna) ---

    private fun uSustavuPdv(): Boolean = selected.value?.uSustavuPdv == true
    private fun fmt(b: BigDecimal): String = b.setScale(2, RoundingMode.HALF_UP).toPlainString()

    fun ukloniStavku(index: Int) { stavke.removeAt(index) }

    /**
     * Brzi tap-dodaj s grida artikala (Novi račun): ako stavka za ovaj artikl već
     * postoji (isti naziv/cijena/PDV, bez ručnog popusta) samo joj poveća količinu za
     * 1, inače doda novu stavku s količinom 1 — bez otvaranja dijaloga.
     */
    fun dodajIliPovecajStavku(a: Artikl) {
        val cijena = fmt(a.jedCijena)
        val stopa = fmt(a.pdvStopa)
        val idx = stavke.indexOfFirst {
            it.naziv == a.naziv && it.jedCijena == cijena && it.pdvStopa == stopa && it.popust == "0"
        }
        if (idx >= 0) povecajKolicinu(idx) else stavke.add(
            preracunajBazu(StavkaInput(naziv = a.naziv, kolicina = "1", jedMjere = a.jedMjere, jedCijena = cijena, pdvStopa = stopa))
        )
    }

    /** Poveća količinu stavke za 1 (stepper na retku stavke). */
    fun povecajKolicinu(index: Int) {
        val s = stavke[index]
        val nova = parse(s.kolicina).add(BigDecimal.ONE)
        stavke[index] = preracunajBazu(s.copy(kolicina = nova.stripTrailingZeros().toPlainString()))
    }

    /** Smanji količinu stavke za 1 (stepper na retku stavke); ukloni stavku ako padne na 0 ili manje. */
    fun smanjiKolicinu(index: Int) {
        val s = stavke[index]
        val nova = parse(s.kolicina).subtract(BigDecimal.ONE)
        if (nova.signum() <= 0) ukloniStavku(index) else stavke[index] = preracunajBazu(s.copy(kolicina = nova.stripTrailingZeros().toPlainString()))
    }

    /** Otvara dijalog za dodavanje nove stavke iz odabranog artikla. */
    fun zapocniDodavanjeIzArtikla(a: Artikl) {
        uredjivanjeIndex.value = null
        stavkaUnos.value = StavkaUnos(
            naziv = a.naziv,
            jedMjere = a.jedMjere,
            pdvStopa = fmt(a.pdvStopa),
            kolicina = "1",
            jedCijena = fmt(a.jedCijena),
            popust = "0",
        )
    }

    /** Otvara dijalog za uređivanje već potvrđene stavke (količina/cijena/popust). */
    fun zapocniUredjivanjeStavke(index: Int) {
        val s = stavke[index]
        uredjivanjeIndex.value = index
        stavkaUnos.value = StavkaUnos(
            naziv = s.naziv,
            jedMjere = s.jedMjere,
            pdvStopa = s.pdvStopa,
            kolicina = s.kolicina,
            jedCijena = s.jedCijena,
            popust = s.popust,
        )
    }

    fun otkaziUnosStavke() {
        stavkaUnos.value = null
        uredjivanjeIndex.value = null
    }

    fun setUnosKolicina(v: String) { stavkaUnos.value = stavkaUnos.value?.copy(kolicina = v) }
    fun setUnosCijena(v: String) { stavkaUnos.value = stavkaUnos.value?.copy(jedCijena = v) }
    fun setUnosPopust(v: String) { stavkaUnos.value = stavkaUnos.value?.copy(popust = v) }

    /** Pregled ukupnog iznosa za stavku koja se trenutno unosi/uređuje (za prikaz u dijalogu). */
    fun izracunUnosa(): BigDecimal? {
        val u = stavkaUnos.value ?: return null
        return preracunajBazu(
            StavkaInput(naziv = u.naziv, kolicina = u.kolicina, jedMjere = u.jedMjere, jedCijena = u.jedCijena, pdvStopa = u.pdvStopa, popust = u.popust)
        ).ukupno.let { parse(it) }
    }

    /** Potvrđuje unos (dodaje novu stavku ili sprema izmjenu postojeće). */
    fun potvrdiUnosStavke() {
        val u = stavkaUnos.value ?: return
        val novo = preracunajBazu(
            StavkaInput(naziv = u.naziv, kolicina = u.kolicina, jedMjere = u.jedMjere, jedCijena = u.jedCijena, pdvStopa = u.pdvStopa, popust = u.popust)
        )
        val idx = uredjivanjeIndex.value
        if (idx != null) stavke[idx] = novo else stavke.add(novo)
        otkaziUnosStavke()
    }

    /** Iz količine, jedinične cijene i popusta na stavci izračuna neto, pa PDV i ukupno. */
    private fun preracunajBazu(s: StavkaInput): StavkaInput {
        val bazniNeto = parse(s.kolicina).multiply(parse(s.jedCijena))
        val faktorPopusta = BigDecimal.ONE.subtract(parse(s.popust).divide(BigDecimal(100)))
        val neto = bazniNeto.multiply(faktorPopusta).setScale(2, RoundingMode.HALF_UP)
        return if (uSustavuPdv()) {
            val pdv = neto.multiply(parse(s.pdvStopa)).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
            s.copy(neto = fmt(neto), pdvIznos = fmt(pdv), ukupno = fmt(neto.add(pdv)))
        } else {
            s.copy(neto = fmt(neto), pdvIznos = "0.00", ukupno = fmt(neto))
        }
    }

    fun setPopustRacuna(v: String) { popustRacuna.value = v }

    /** Faktor popusta na razini računa (1 = bez popusta, 0.9 = 10% popusta). */
    private fun popustRacunaFaktor(): BigDecimal = BigDecimal.ONE.subtract(parse(popustRacuna.value).divide(BigDecimal(100)))

    fun ukupno(): BigDecimal = zbroj { it.ukupno }
    fun zbrojNeto(): BigDecimal = zbroj { it.neto }
    fun zbrojPdv(): BigDecimal = zbroj { it.pdvIznos }

    private inline fun zbroj(selector: (StavkaInput) -> String): BigDecimal =
        stavke.fold(BigDecimal.ZERO) { acc, s -> acc.add(parse(selector(s))) }
            .multiply(popustRacunaFaktor())
            .setScale(2, RoundingMode.HALF_UP)

    /** Ima li obrazac nespremljenog unosa (za potvrdu izlaza). */
    fun imaUnos(): Boolean =
        stavke.isNotEmpty() || stavkaUnos.value != null ||
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
            // Osvježi tvrtku/selekciju nakon svakog pokušaja (povecajBroj se u izvrsi()
            // poziva bez obzira na ishod, pa selekciju treba osvježiti da predloženi
            // broj za sljedeći račun bude točan).
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
                val faktor = popustRacunaFaktor()
                fun iznos(x: String) = parse(x).multiply(faktor).setScale(2, RoundingMode.HALF_UP)
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
        // Broj se povećava nakon SVAKOG pokušaja (ne samo uspješnog) — ZKI je već
        // izračunat i račun je mogao biti otisnut/predan kupcu bez obzira je li CIS
        // potvrdio JIR, pa isti broj/prostor/uređaj ne smije biti dodijeljen dvaput.
        // Ponovni pokušaj (naknadna dostava) namjerno NE prolazi kroz ovu funkciju
        // već kroz ponoviIzvrsi(), koji šalje ISTI broj — ovdje se broj uvijek odnosi
        // na potpuno nov račun.
        companyStore.povecajBroj(t.id, djelatnost.id, prostor.id, uredjaj.id)
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
                kupacAdresa = kupacAdresa.value,
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
        if (stavke.isEmpty()) return "Dodaj barem jednu stavku računa."
        return null
    }

    fun resetRacun() {
        val t = selected.value
        stavke.clear()
        stavkaUnos.value = null
        uredjivanjeIndex.value = null
        nacinPlac.value = t?.zadaniNacinPlac ?: NacinPlac.G
        popustRacuna.value = "0"
        kupacNaziv.value = ""
        kupacOib.value = ""
        kupacAdresa.value = ""
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

    // --- Šifrarnik partnera ---
    fun loadPartners() {
        val t = selected.value ?: return
        partners.clear()
        partners.addAll(partnerStore.zaTvrtku(t.id))
    }

    fun newPartner() { editingPartner.value = Partner() }
    fun editPartner(p: Partner) { editingPartner.value = p }

    fun savePartner(p: Partner) {
        selected.value?.let { partnerStore.spremi(it.id, p) }
        loadPartners()
        editingPartner.value = null
    }

    fun deletePartner(p: Partner) {
        selected.value?.let { partnerStore.obrisi(it.id, p.id) }
        loadPartners()
        editingPartner.value = null
    }

    /** Popuni kupca podacima partnera (i dalje ručno promjenjivo — nije zaključano). */
    fun odaberiPartnera(p: Partner) {
        kupacNaziv.value = p.naziv
        kupacOib.value = p.oib
        kupacAdresa.value = p.adresa
    }

    /** Učita stavke i podatke postojećeg računa u novi obrazac (za ponovno izdavanje/ispravak). */
    fun kopirajURacun(si: SavedInvoice) = ucitajURacun(si, negiraj = false)

    /**
     * Kopira postojeći račun u novi obrazac, ali s negativnim iznosima (storno) —
     * za brzo stornirianje već fiskaliziranog računa.
     */
    fun stornirajRacun(si: SavedInvoice) = ucitajURacun(si, negiraj = true)

    private fun ucitajURacun(si: SavedInvoice, negiraj: Boolean) {
        stavke.clear()
        si.racun.stavke.forEach { s ->
            val predznak = if (negiraj) -1 else 1
            val neto = s.neto.abs().multiply(BigDecimal(predznak))
            val jed = (if (s.kolicina.signum() != 0) s.neto.abs().divide(s.kolicina, 2, RoundingMode.HALF_UP) else s.neto.abs())
                .multiply(BigDecimal(predznak))
            stavke.add(
                StavkaInput(
                    naziv = s.naziv,
                    kolicina = s.kolicina.toPlainString(),
                    jedMjere = s.jedMjere,
                    jedCijena = fmt(jed),
                    pdvStopa = fmt(s.pdvStopa),
                    neto = fmt(neto),
                    pdvIznos = fmt(s.pdvIznos.abs().multiply(BigDecimal(predznak))),
                    ukupno = fmt(s.ukupno.abs().multiply(BigDecimal(predznak))),
                )
            )
        }
        kupacNaziv.value = si.kupac
        kupacOib.value = si.kupacOib
        kupacAdresa.value = si.kupacAdresa
        napomena.value = si.napomena
        popustRacuna.value = "0"
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

    /** Broj računa i promet za danas (odabrana tvrtka, svi računi — fiskalizirani i nefiskalizirani). Za prikaz na početnoj. */
    fun statistikaDanas(): Pair<Int, BigDecimal> {
        val t = selected.value ?: return 0 to BigDecimal.ZERO
        val danas = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val danasnji = invoiceStore.zaTvrtku(t.id).filter { it.createdAt >= danas }
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

    // --- Izvještaji ---

    /**
     * Izračuna izvještaj za odabranu tvrtku i period. Obuhvaća SVE izdane račune,
     * fiskalizirane i nefiskalizirane — račun je izdan (ZKI izračunat, mogao je
     * biti otisnut) i kad CIS još nije potvrdio JIR.
     * Za [PeriodIzvjestaja.PRILAGODJENO] koriste se [prilagodjenoOd]/[prilagodjenoDo]
     * (epoch millis, uključivo — dopuštaju odabir do razine minute).
     */
    fun izvjestaj(period: PeriodIzvjestaja, prilagodjenoOd: Long? = null, prilagodjenoDo: Long? = null): Izvjestaj {
        val t = selected.value ?: return Izvjestaj(0, BigDecimal.ZERO, emptyList(), emptyList(), emptyList())
        val svi = invoiceStore.zaTvrtku(t.id)
        val filtrirano = if (period == PeriodIzvjestaja.PRILAGODJENO) {
            svi.filter { si ->
                (prilagodjenoOd == null || si.createdAt >= prilagodjenoOd) &&
                    (prilagodjenoDo == null || si.createdAt <= prilagodjenoDo)
            }
        } else {
            val od = period.pocetak()
            if (od == null) svi else svi.filter { it.createdAt >= od }
        }

        val brojRacuna = filtrirano.size
        val ukupanPromet = filtrirano.zbrojRacuna { it.racun.iznosUkupno }

        val poNacinu = filtrirano.groupBy { it.racun.nacinPlac }
            .map { (nacin, lista) -> StavkaNacinPlac(nacin, lista.size, lista.zbrojRacuna { it.racun.iznosUkupno }) }
            .sortedByDescending { it.ukupno.abs() }

        val pdvOsnovica = mutableMapOf<String, BigDecimal>()
        val pdvIznos = mutableMapOf<String, BigDecimal>()
        filtrirano.forEach { si ->
            si.racun.pdvGrupe().forEach { g ->
                val key = fmt(g.stopa)
                pdvOsnovica[key] = (pdvOsnovica[key] ?: BigDecimal.ZERO).add(g.osnovica)
                pdvIznos[key] = (pdvIznos[key] ?: BigDecimal.ZERO).add(g.iznos)
            }
        }
        val pdvRekapitulacija = pdvOsnovica.keys.sortedBy { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }
            .map { stopa ->
                val osn = pdvOsnovica[stopa] ?: BigDecimal.ZERO
                val pdv = pdvIznos[stopa] ?: BigDecimal.ZERO
                StavkaPdv(stopa, fmt2(osn), fmt2(pdv), fmt2(osn.add(pdv)))
            }

        val artKolicina = mutableMapOf<String, BigDecimal>()
        val artUkupno = mutableMapOf<String, BigDecimal>()
        val artJedMjere = mutableMapOf<String, String>()
        filtrirano.forEach { si ->
            si.racun.stavke.forEach { s ->
                artKolicina[s.naziv] = (artKolicina[s.naziv] ?: BigDecimal.ZERO).add(s.kolicina)
                artUkupno[s.naziv] = (artUkupno[s.naziv] ?: BigDecimal.ZERO).add(s.ukupno)
                if (s.jedMjere.isNotBlank()) artJedMjere.putIfAbsent(s.naziv, s.jedMjere)
            }
        }
        val poArtiklima = artUkupno.keys
            .map { naziv ->
                StavkaArtikl(naziv, artKolicina[naziv] ?: BigDecimal.ZERO, fmt2(artUkupno[naziv] ?: BigDecimal.ZERO), artJedMjere[naziv] ?: "kom")
            }
            .sortedByDescending { it.ukupno.abs() }

        return Izvjestaj(brojRacuna, ukupanPromet, poNacinu, pdvRekapitulacija, poArtiklima)
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
    private fun fmt2(b: BigDecimal): BigDecimal = b.setScale(2, RoundingMode.HALF_UP)
    private inline fun List<SavedInvoice>.zbrojRacuna(selector: (SavedInvoice) -> BigDecimal): BigDecimal =
        fold(BigDecimal.ZERO) { acc, si -> acc.add(selector(si)) }.setScale(2, RoundingMode.HALF_UP)

    fun obrisiIzPovijesti(si: SavedInvoice) {
        invoiceStore.obrisi(si)
        loadHistory()
    }

    /**
     * Ponovni pokušaj fiskalizacije već spremljenog (nefiskaliziranog) računa —
     * "naknadna dostava". Šalje se ISTI račun (isti broj, iznos, stavke → isti
     * ZKI kao na već otisnutom računu), samo označen NakDost=true.
     */
    fun ponoviFiskalizaciju(si: SavedInvoice) {
        if (si.jir != null) return
        greska.value = null
        ucitavanje.value = true
        viewModelScope.launch {
            val rezultat = withContext(Dispatchers.IO) { ponoviIzvrsi(si) }
            ucitavanje.value = false
            rezultat.fold(
                onSuccess = { azurirani -> detail.value = azurirani; loadHistory() },
                onFailure = { greska.value = it.message ?: "Nepoznata greška." },
            )
        }
    }

    private fun ponoviIzvrsi(si: SavedInvoice): Result<SavedInvoice> = runCatching {
        val t = companies.firstOrNull { it.id == si.companyId }
            ?: throw IllegalStateException("Tvrtka nije pronađena.")
        val bytes = companyStore.certBytes(t.id)
            ?: throw IllegalStateException("Certifikat nije učitan (Postavke tvrtke).")
        val cert = try {
            FiskalCertificate.load(bytes.inputStream(), companyStore.lozinka(t.id).toCharArray())
        } catch (e: Exception) {
            throw IllegalStateException("Ne mogu otvoriti certifikat — provjeri lozinku certifikata u Postavkama. (${e.message})")
        }
        val caCerts = CaStore.loadExtraCas(getApplication(), companyStore.caBytes(t.id))
        val service = FiskalService(cert, t.okolina, t.ignoreTls, caCerts)

        val racunZaSlanje = si.racun.copy(nakDost = true)
        val ishod = service.fiskaliziraj(racunZaSlanje)

        val status = when (val r = ishod.rezultat) {
            is FiskalRezultat.Uspjeh -> "Fiskaliziran"
            is FiskalRezultat.Greska -> "CIS greška: ${r.sifra} ${r.poruka}"
            is FiskalRezultat.Neizvjesno -> "NEIZVJESNO — provjeri (možda fiskalizirano)"
            is FiskalRezultat.Mreza -> "Nije poslano: ${r.poruka}"
        }
        val azurirani = si.copy(racun = racunZaSlanje, jir = ishod.jir, zki = ishod.zki, qrUrl = ishod.qrUrl, status = status)
        invoiceStore.spremi(azurirani)
        azurirani
    }

    // --- Ispis / email ---
    fun receiptFromIshod(): ReceiptData? {
        val i = ishod.value ?: return null
        val t = selected.value
        return ReceiptData(
            naslovTvrtke = t?.opis() ?: "",
            racun = i.racun, jir = i.jir, zki = i.zki, qrUrl = i.qrUrl,
            kupac = kupacNaziv.value, kupacOib = kupacOib.value, kupacAdresa = kupacAdresa.value, napomena = napomena.value,
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
        kupacAdresa = si.kupacAdresa,
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
