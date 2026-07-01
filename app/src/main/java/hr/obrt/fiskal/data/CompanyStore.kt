package hr.obrt.fiskal.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pohrana više tvrtki, odabrane tvrtke te po-tvrtki certifikata (.p12), FINA CA i
 * lozinke. Konfiguracija je u EncryptedSharedPreferences, a datoteke u privatnoj
 * internoj pohrani (po id-u tvrtke).
 *
 * Pri prvom pokretanju migrira staru (jednotvrtkinu) konfiguraciju.
 */
class CompanyStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey by lazy {
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext, "fiskal_companies", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    init {
        migrirajStaruKonfiguraciju()
    }

    fun sve(): List<Tvrtka> {
        val raw = prefs.getString(K_LISTA, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Tvrtka.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun spremiSve(list: List<Tvrtka>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(K_LISTA, arr.toString()).apply()
    }

    fun spremiTvrtku(t: Tvrtka) {
        val list = sve().toMutableList()
        val idx = list.indexOfFirst { it.id == t.id }
        if (idx >= 0) list[idx] = t else list.add(t)
        spremiSve(list)
        if (odabranaId == null) odabranaId = t.id
    }

    fun obrisiTvrtku(id: String) {
        spremiSve(sve().filterNot { it.id == id })
        certFile(id).delete()
        caFile(id).delete()
        prefs.edit().remove(passKey(id)).apply()
        if (odabranaId == id) odabranaId = sve().firstOrNull()?.id
    }

    var odabranaId: String?
        get() = prefs.getString(K_ODABRANA, null)
        set(v) = prefs.edit().putString(K_ODABRANA, v).apply()

    fun odabrana(): Tvrtka? = sve().firstOrNull { it.id == odabranaId }

    /**
     * Povećava sljedeći broj računa na ispravnoj razini: po poslovnom prostoru
     * (OznSlijed = P) ili po naplatnom uređaju (OznSlijed = N).
     */
    fun povecajBroj(tvrtkaId: String, djelatnostId: String, prostorId: String, uredjajId: String) {
        val t = sve().firstOrNull { it.id == tvrtkaId } ?: return
        val d = t.djelatnosti.firstOrNull { it.id == djelatnostId } ?: return
        val p = d.poslovniProstori.firstOrNull { it.id == prostorId } ?: return
        if (d.oznSlijed == hr.obrt.fiskal.model.OznSlijed.P) {
            p.sljedeciBroj += 1
        } else {
            val u = p.naplatniUredjaji.firstOrNull { it.id == uredjajId } ?: return
            u.sljedeciBroj += 1
        }
        spremiTvrtku(t)
    }

    // --- Certifikat (.p12) po tvrtki ---
    fun certFile(id: String): File = File(appContext.filesDir, "cert_$id.p12")
    fun spremiCert(id: String, bytes: ByteArray) = certFile(id).writeBytes(bytes)
    fun certPostoji(id: String): Boolean = certFile(id).let { it.exists() && it.length() > 0 }
    fun certBytes(id: String): ByteArray? = if (certPostoji(id)) certFile(id).readBytes() else null
    fun obrisiCert(id: String) {
        certFile(id).delete()
        postaviLozinku(id, "")
    }

    // --- Lozinka certifikata po tvrtki ---
    fun lozinka(id: String): String = prefs.getString(passKey(id), "") ?: ""
    fun postaviLozinku(id: String, v: String) = prefs.edit().putString(passKey(id), v).apply()

    // --- FINA CA po tvrtki (opcionalno; uz ugrađeni res/raw/fina_ca) ---
    fun caFile(id: String): File = File(appContext.filesDir, "ca_$id.pem")
    fun spremiCa(id: String, bytes: ByteArray) = caFile(id).writeBytes(bytes)
    fun caPostoji(id: String): Boolean = caFile(id).let { it.exists() && it.length() > 0 }
    fun caBytes(id: String): ByteArray? = if (caPostoji(id)) caFile(id).readBytes() else null
    fun obrisiCa(id: String) { caFile(id).delete() }

    private fun passKey(id: String) = "pass_$id"

    private fun migrirajStaruKonfiguraciju() {
        if (prefs.getBoolean(K_MIGRIRANO, false)) return
        if (sve().isNotEmpty()) {
            prefs.edit().putBoolean(K_MIGRIRANO, true).apply()
            return
        }
        val legacy = runCatching {
            EncryptedSharedPreferences.create(
                appContext, "fiskal_secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
        val staraCert = File(appContext.filesDir, "fina_cert.p12")
        val staraCa = File(appContext.filesDir, "fina_ca.pem")
        val oib = legacy?.getString("oib", "") ?: ""

        if (oib.isNotBlank() || staraCert.exists()) {
            val uredjaj = NaplatniUredaj(
                oznaka = legacy?.getString("ozn_nap_ur", "1") ?: "1",
                sljedeciBroj = legacy?.getLong("sljedeci_broj", 1L) ?: 1L,
            )
            val prostor = PoslovniProstor(
                oznaka = legacy?.getString("ozn_pos_pr", "POSL1") ?: "POSL1",
                sljedeciBroj = legacy?.getLong("sljedeci_broj", 1L) ?: 1L,
                naplatniUredjaji = mutableListOf(uredjaj),
            )
            val djelatnost = Djelatnost(
                naziv = "Glavna djelatnost",
                oznSlijed = runCatching {
                    hr.obrt.fiskal.model.OznSlijed.valueOf(legacy?.getString("ozn_slijed", "P") ?: "P")
                }.getOrDefault(hr.obrt.fiskal.model.OznSlijed.P),
                poslovniProstori = mutableListOf(prostor),
                zadaniPoslovniProstorId = prostor.id,
                zadaniNaplatniUredjajId = uredjaj.id,
            )
            val t = Tvrtka(
                naziv = if (oib.isNotBlank()) "Tvrtka $oib" else "Moja tvrtka",
                oib = oib,
                uSustavuPdv = legacy?.getBoolean("u_sustavu_pdv", false) ?: false,
                oibOper = legacy?.getString("oib_oper", "") ?: "",
                okolina = runCatching {
                    hr.obrt.fiskal.fiskal.FiskalOkolina.valueOf(legacy?.getString("okolina", "TEST") ?: "TEST")
                }.getOrDefault(hr.obrt.fiskal.fiskal.FiskalOkolina.TEST),
                ignoreTls = legacy?.getBoolean("ignore_tls", false) ?: false,
                djelatnosti = mutableListOf(djelatnost),
                zadanaDjelatnostId = djelatnost.id,
            )
            if (staraCert.exists()) runCatching { staraCert.copyTo(certFile(t.id), overwrite = true) }
            if (staraCa.exists()) runCatching { staraCa.copyTo(caFile(t.id), overwrite = true) }
            legacy?.getString("cert_pass", "")?.let { if (it.isNotBlank()) postaviLozinku(t.id, it) }
            spremiTvrtku(t)
            odabranaId = t.id
        }
        prefs.edit().putBoolean(K_MIGRIRANO, true).apply()
    }

    companion object {
        private const val K_LISTA = "tvrtke"
        private const val K_ODABRANA = "odabrana_id"
        private const val K_MIGRIRANO = "migrirano_v1"
    }
}
