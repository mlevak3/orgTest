package hr.obrt.fiskal.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Pohrana spremljenih računa po tvrtki (JSON datoteke u internoj pohrani). */
class InvoiceStore(context: Context) {

    private val baseDir = File(context.applicationContext.filesDir, "invoices")

    private fun companyDir(companyId: String): File =
        File(baseDir, companyId).apply { mkdirs() }

    fun spremi(invoice: SavedInvoice) {
        val dir = companyDir(invoice.companyId)
        File(dir, "${invoice.createdAt}_${invoice.id}.json")
            .writeText(invoice.toJson().toString())
    }

    /** Računi tvrtke, najnoviji prvi. */
    fun zaTvrtku(companyId: String): List<SavedInvoice> {
        val dir = companyDir(companyId)
        val files = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.sortedByDescending { it.name }.mapNotNull { f ->
            runCatching { SavedInvoice.fromJson(JSONObject(f.readText())) }.getOrNull()
        }
    }

    fun obrisi(invoice: SavedInvoice) {
        companyDir(invoice.companyId)
            .listFiles { f -> f.name.contains(invoice.id) }
            ?.forEach { it.delete() }
    }
}
