package hr.obrt.fiskal.data

import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Jedna tvrtka/obrt s vlastitim postavkama fiskalizacije.
 *
 * Tvrtka može imati više djelatnosti; svaka djelatnost ima svoje poslovne
 * prostore i naplatne uređaje (i zadani par koji se ponudi pri izradi računa).
 */
data class Tvrtka(
    val id: String = UUID.randomUUID().toString(),
    var naziv: String = "",
    var oib: String = "",
    var uSustavuPdv: Boolean = false,
    var oibOper: String = "",
    var okolina: FiskalOkolina = FiskalOkolina.TEST,
    var ignoreTls: Boolean = false,
    /** MAC adresa uparenog Bluetooth POS pisača (npr. Bixolon SPP-R200II), ili prazno. */
    var printerAddress: String = "",
    // --- Zadane vrijednosti (ubrzavaju unos novog računa/artikla) ---
    var zadanaPdvStopa: String = "25",
    var zadaniNacinPlac: NacinPlac = NacinPlac.G,
    var zadanaJedMjere: String = "kom",
    var djelatnosti: MutableList<Djelatnost> = mutableListOf(Djelatnost()),
    var zadanaDjelatnostId: String = "",
) {
    fun opis(): String = naziv.ifBlank { oib.ifBlank { "(bez naziva)" } }

    fun zadanaDjelatnost(): Djelatnost =
        djelatnosti.firstOrNull { it.id == zadanaDjelatnostId } ?: djelatnosti.first()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("naziv", naziv)
        put("oib", oib)
        put("uSustavuPdv", uSustavuPdv)
        put("oibOper", oibOper)
        put("okolina", okolina.name)
        put("ignoreTls", ignoreTls)
        put("printerAddress", printerAddress)
        put("zadanaPdvStopa", zadanaPdvStopa)
        put("zadaniNacinPlac", zadaniNacinPlac.oznaka)
        put("zadanaJedMjere", zadanaJedMjere)
        put("djelatnosti", JSONArray().apply { djelatnosti.forEach { put(it.toJson()) } })
        put("zadanaDjelatnostId", zadanaDjelatnostId)
    }

    companion object {
        fun fromJson(o: JSONObject): Tvrtka {
            val djArr = o.optJSONArray("djelatnosti")
            val djelatnosti: MutableList<Djelatnost> = if (djArr != null && djArr.length() > 0) {
                (0 until djArr.length()).map { Djelatnost.fromJson(djArr.getJSONObject(it)) }.toMutableList()
            } else {
                // Migracija sa starije (jednodjelatnosne) strukture tvrtke — čuva postojeći
                // poslovni prostor, naplatni uređaj, oznaku slijednosti i broj računa.
                val uredjaj = NaplatniUredaj(
                    oznaka = o.optString("oznNapUr", "1"),
                    sljedeciBroj = o.optLong("sljedeciBroj", 1L),
                )
                val prostor = PoslovniProstor(
                    oznaka = o.optString("oznPosPr", "POSL1"),
                    sljedeciBroj = o.optLong("sljedeciBroj", 1L),
                    naplatniUredjaji = mutableListOf(uredjaj),
                )
                mutableListOf(
                    Djelatnost(
                        naziv = "Glavna djelatnost",
                        oznSlijed = runCatching { OznSlijed.valueOf(o.optString("oznSlijed", "P")) }.getOrDefault(OznSlijed.P),
                        poslovniProstori = mutableListOf(prostor),
                        zadaniPoslovniProstorId = prostor.id,
                        zadaniNaplatniUredjajId = uredjaj.id,
                    )
                )
            }
            return Tvrtka(
                id = o.optString("id", UUID.randomUUID().toString()),
                naziv = o.optString("naziv"),
                oib = o.optString("oib"),
                uSustavuPdv = o.optBoolean("uSustavuPdv"),
                oibOper = o.optString("oibOper"),
                okolina = runCatching { FiskalOkolina.valueOf(o.optString("okolina", "TEST")) }.getOrDefault(FiskalOkolina.TEST),
                ignoreTls = o.optBoolean("ignoreTls"),
                printerAddress = o.optString("printerAddress"),
                zadanaPdvStopa = o.optString("zadanaPdvStopa", "25"),
                zadaniNacinPlac = NacinPlac.fromOznaka(o.optString("zadaniNacinPlac", "G")),
                zadanaJedMjere = o.optString("zadanaJedMjere", "kom"),
                djelatnosti = djelatnosti,
                zadanaDjelatnostId = o.optString("zadanaDjelatnostId").ifBlank { djelatnosti.first().id },
            )
        }
    }
}
