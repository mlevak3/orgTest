package hr.obrt.fiskal.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Šifrarnik artikala po tvrtki (JSON datoteka u internoj pohrani). */
class ArticleStore(context: Context) {

    private val baseDir = File(context.applicationContext.filesDir, "articles").apply { mkdirs() }

    private fun file(companyId: String) = File(baseDir, "$companyId.json")

    fun zaTvrtku(companyId: String): List<Artikl> {
        val f = file(companyId)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { Artikl.fromJson(arr.getJSONObject(it)) }
                .sortedBy { it.naziv.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun spremiSve(companyId: String, list: List<Artikl>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file(companyId).writeText(arr.toString())
    }

    fun spremi(companyId: String, artikl: Artikl) {
        val list = zaTvrtku(companyId).toMutableList()
        val idx = list.indexOfFirst { it.id == artikl.id }
        if (idx >= 0) list[idx] = artikl else list.add(artikl)
        spremiSve(companyId, list)
    }

    fun obrisi(companyId: String, id: String) {
        spremiSve(companyId, zaTvrtku(companyId).filterNot { it.id == id })
    }
}
