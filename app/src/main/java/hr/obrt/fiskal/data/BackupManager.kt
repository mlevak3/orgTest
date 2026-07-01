package hr.obrt.fiskal.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Izvoz/uvoz svih podataka aplikacije (tvrtke, certifikati, FINA CA, lozinke,
 * šifrarnik, povijest računa) u jednu JSON datoteku.
 *
 * Datoteka sadrži OSJETLJIVE podatke (privatni ključ certifikata i lozinku) —
 * treba je čuvati sigurno i ne dijeliti nezaštićeno.
 */
object BackupManager {

    private const val VERSION = 1

    fun export(context: Context): String {
        val companyStore = CompanyStore(context)
        val articleStore = ArticleStore(context)
        val invoiceStore = InvoiceStore(context)

        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val companiesArr = JSONArray()
        companyStore.sve().forEach { t ->
            val o = t.toJson()
            o.put("certB64", companyStore.certBytes(t.id)?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
            o.put("certPassword", companyStore.lozinka(t.id))
            o.put("caB64", companyStore.caBytes(t.id)?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)

            val articlesArr = JSONArray()
            articleStore.zaTvrtku(t.id).forEach { articlesArr.put(it.toJson()) }
            o.put("articles", articlesArr)

            val invoicesArr = JSONArray()
            invoiceStore.zaTvrtku(t.id).forEach { invoicesArr.put(it.toJson()) }
            o.put("invoices", invoicesArr)

            companiesArr.put(o)
        }
        root.put("companies", companiesArr)
        return root.toString(2)
    }

    /** Vraća broj uvezenih tvrtki. Postojeće tvrtke s istim id-om se zamjenjuju. */
    fun import(context: Context, json: String): Result<Int> = runCatching {
        val companyStore = CompanyStore(context)
        val articleStore = ArticleStore(context)
        val invoiceStore = InvoiceStore(context)

        val root = JSONObject(json)
        val companiesArr = root.optJSONArray("companies") ?: JSONArray()
        for (i in 0 until companiesArr.length()) {
            val o = companiesArr.getJSONObject(i)
            val t = Tvrtka.fromJson(o)
            companyStore.spremiTvrtku(t)

            if (!o.isNull("certB64")) {
                val bytes = Base64.decode(o.optString("certB64"), Base64.NO_WRAP)
                companyStore.spremiCert(t.id, bytes)
            }
            o.optString("certPassword", "").let { if (it.isNotBlank()) companyStore.postaviLozinku(t.id, it) }
            if (!o.isNull("caB64")) {
                val bytes = Base64.decode(o.optString("caB64"), Base64.NO_WRAP)
                companyStore.spremiCa(t.id, bytes)
            }

            val articlesArr = o.optJSONArray("articles") ?: JSONArray()
            for (j in 0 until articlesArr.length()) {
                articleStore.spremi(t.id, Artikl.fromJson(articlesArr.getJSONObject(j)))
            }

            val invoicesArr = o.optJSONArray("invoices") ?: JSONArray()
            for (j in 0 until invoicesArr.length()) {
                invoiceStore.spremi(SavedInvoice.fromJson(invoicesArr.getJSONObject(j)))
            }
        }
        companiesArr.length()
    }
}
