package hr.obrt.fiskal.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Jedan zapis u logu fiskalizacije — zahtjev i odgovor jednog pokušaja slanja na CIS. */
data class FiskalLogUnos(
    val id: String = UUID.randomUUID().toString(),
    val companyId: String,
    val vrijeme: Long,
    val brojRacuna: String,
    val nakDost: Boolean,
    val ishod: String,
    val httpKod: Int,
    val requestXml: String,
    val responseXml: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("companyId", companyId)
        put("vrijeme", vrijeme)
        put("brojRacuna", brojRacuna)
        put("nakDost", nakDost)
        put("ishod", ishod)
        put("httpKod", httpKod)
        put("requestXml", requestXml)
        put("responseXml", responseXml)
    }

    companion object {
        fun fromJson(o: JSONObject) = FiskalLogUnos(
            id = o.optString("id", UUID.randomUUID().toString()),
            companyId = o.optString("companyId"),
            vrijeme = o.optLong("vrijeme"),
            brojRacuna = o.optString("brojRacuna"),
            nakDost = o.optBoolean("nakDost"),
            ishod = o.optString("ishod"),
            httpKod = o.optInt("httpKod", -1),
            requestXml = o.optString("requestXml"),
            responseXml = o.optString("responseXml"),
        )
    }
}

/**
 * Pohrana loga fiskalizacije (SOAP zahtjev i odgovor svakog pokušaja, uspješnog ili
 * ne) po tvrtki — za dijagnostiku CIS grešaka. Drži samo zadnjih [MAX_UNOSA] zapisa
 * po tvrtki (najstariji se brišu) da log ne raste neograničeno.
 */
class FiskalLogStore(context: Context) {

    private val baseDir = File(context.applicationContext.filesDir, "fiskal_log")

    private fun companyDir(companyId: String): File =
        File(baseDir, companyId).apply { mkdirs() }

    fun zapisi(unos: FiskalLogUnos) {
        val dir = companyDir(unos.companyId)
        File(dir, "${unos.vrijeme}_${unos.id}.json").writeText(unos.toJson().toString())
        obrezi(dir)
    }

    /** Zapisi tvrtke, najnoviji prvi. */
    fun zaTvrtku(companyId: String): List<FiskalLogUnos> {
        val dir = companyDir(companyId)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.sortedByDescending { it.name }.mapNotNull { f ->
            runCatching { FiskalLogUnos.fromJson(JSONObject(f.readText())) }.getOrNull()
        }
    }

    fun obrisiSve(companyId: String) {
        companyDir(companyId).listFiles()?.forEach { it.delete() }
    }

    private fun obrezi(dir: File) {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return
        if (files.size <= MAX_UNOSA) return
        files.sortedByDescending { it.name }.drop(MAX_UNOSA).forEach { it.delete() }
    }

    companion object {
        private const val MAX_UNOSA = 200
    }
}
