package hr.obrt.fiskal.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Šifrarnik partnera (kupaca) po tvrtki (JSON datoteka u internoj pohrani). */
class PartnerStore(context: Context) {

    private val baseDir = File(context.applicationContext.filesDir, "partners").apply { mkdirs() }

    private fun file(companyId: String) = File(baseDir, "$companyId.json")

    fun zaTvrtku(companyId: String): List<Partner> {
        val f = file(companyId)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { Partner.fromJson(arr.getJSONObject(it)) }
                .sortedBy { it.naziv.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun spremiSve(companyId: String, list: List<Partner>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file(companyId).writeText(arr.toString())
    }

    fun spremi(companyId: String, partner: Partner) {
        val list = zaTvrtku(companyId).toMutableList()
        val idx = list.indexOfFirst { it.id == partner.id }
        if (idx >= 0) list[idx] = partner else list.add(partner)
        spremiSve(companyId, list)
    }

    fun obrisi(companyId: String, id: String) {
        spremiSve(companyId, zaTvrtku(companyId).filterNot { it.id == id })
    }
}
