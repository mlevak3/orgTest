package hr.obrt.fiskal.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Zaglavlje
import java.io.File

/**
 * Postavke obveznika i FINA certifikat. Lozinka certifikata i konfiguracija
 * čuvaju se u EncryptedSharedPreferences; sam .p12 spremnik u privatnoj datoteci
 * aplikacije (interna pohrana, nedostupna drugim aplikacijama).
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "fiskal_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val certFile: File get() = File(appContext.filesDir, "fina_cert.p12")
    private val caFile: File get() = File(appContext.filesDir, "fina_ca.pem")

    var oib: String
        get() = prefs.getString(K_OIB, "") ?: ""
        set(v) = prefs.edit().putString(K_OIB, v).apply()

    var uSustavuPdv: Boolean
        get() = prefs.getBoolean(K_PDV, false)
        set(v) = prefs.edit().putBoolean(K_PDV, v).apply()

    var oznPosPr: String
        get() = prefs.getString(K_POSPR, "POSL1") ?: "POSL1"
        set(v) = prefs.edit().putString(K_POSPR, v).apply()

    var oznNapUr: String
        get() = prefs.getString(K_NAPUR, "1") ?: "1"
        set(v) = prefs.edit().putString(K_NAPUR, v).apply()

    var oznSlijed: OznSlijed
        get() = OznSlijed.valueOf(prefs.getString(K_SLIJED, OznSlijed.P.name) ?: OznSlijed.P.name)
        set(v) = prefs.edit().putString(K_SLIJED, v.name).apply()

    var oibOper: String
        get() = prefs.getString(K_OPER, "") ?: ""
        set(v) = prefs.edit().putString(K_OPER, v).apply()

    var okolina: FiskalOkolina
        get() = FiskalOkolina.valueOf(prefs.getString(K_OKOLINA, FiskalOkolina.TEST.name) ?: FiskalOkolina.TEST.name)
        set(v) = prefs.edit().putString(K_OKOLINA, v.name).apply()

    var ignoreTlsTrust: Boolean
        get() = prefs.getBoolean(K_TLS, false)
        set(v) = prefs.edit().putBoolean(K_TLS, v).apply()

    var certPassword: String
        get() = prefs.getString(K_CERT_PASS, "") ?: ""
        set(v) = prefs.edit().putString(K_CERT_PASS, v).apply()

    var sljedeciBroj: Long
        get() = prefs.getLong(K_BROJ, 1L)
        set(v) = prefs.edit().putLong(K_BROJ, v).apply()

    val zaglavlje: Zaglavlje
        get() = Zaglavlje(
            oib = oib,
            uSustavuPdv = uSustavuPdv,
            oznPosPr = oznPosPr,
            oznNapUr = oznNapUr,
            oznSlijed = oznSlijed,
            oibOper = oibOper.ifBlank { oib },
        )

    fun spremiCertifikat(bytes: ByteArray) = certFile.writeBytes(bytes)
    fun certifikatPostoji(): Boolean = certFile.exists() && certFile.length() > 0
    fun certifikatBytes(): ByteArray? = if (certifikatPostoji()) certFile.readBytes() else null
    fun obrisiCertifikat() {
        certFile.delete()
        certPassword = ""
    }

    // FINA CA certifikat(i) za TLS povjerenje prema produkcijskom CIS-u.
    fun spremiCa(bytes: ByteArray) = caFile.writeBytes(bytes)
    fun caPostoji(): Boolean = caFile.exists() && caFile.length() > 0
    fun caBytes(): ByteArray? = if (caPostoji()) caFile.readBytes() else null
    fun obrisiCa() {
        caFile.delete()
    }

    companion object {
        private const val K_OIB = "oib"
        private const val K_PDV = "u_sustavu_pdv"
        private const val K_POSPR = "ozn_pos_pr"
        private const val K_NAPUR = "ozn_nap_ur"
        private const val K_SLIJED = "ozn_slijed"
        private const val K_OPER = "oib_oper"
        private const val K_OKOLINA = "okolina"
        private const val K_TLS = "ignore_tls"
        private const val K_CERT_PASS = "cert_pass"
        private const val K_BROJ = "sljedeci_broj"
    }
}
