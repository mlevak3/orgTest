package hr.obrt.fiskal.data

import hr.obrt.fiskal.fiskal.FiskalOkolina
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Zaglavlje
import org.json.JSONObject
import java.util.UUID

/** Jedna tvrtka/obrt s vlastitim postavkama fiskalizacije. */
data class Tvrtka(
    val id: String = UUID.randomUUID().toString(),
    var naziv: String = "",
    var oib: String = "",
    var uSustavuPdv: Boolean = false,
    var oznPosPr: String = "POSL1",
    var oznNapUr: String = "1",
    var oznSlijed: OznSlijed = OznSlijed.P,
    var oibOper: String = "",
    var okolina: FiskalOkolina = FiskalOkolina.TEST,
    var ignoreTls: Boolean = false,
    var sljedeciBroj: Long = 1L,
    /** MAC adresa uparenog Bluetooth POS pisača (npr. Bixolon SPP-R200II), ili prazno. */
    var printerAddress: String = "",
    // --- Zadane vrijednosti (ubrzavaju unos novog računa/artikla) ---
    var zadanaPdvStopa: String = "25",
    var zadaniNacinPlac: NacinPlac = NacinPlac.G,
    var zadanaJedMjere: String = "kom",
) {
    fun zaglavlje(): Zaglavlje =
        Zaglavlje(oib, uSustavuPdv, oznPosPr, oznNapUr, oznSlijed, oibOper.ifBlank { oib })

    fun opis(): String = naziv.ifBlank { oib.ifBlank { "(bez naziva)" } }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("naziv", naziv)
        put("oib", oib)
        put("uSustavuPdv", uSustavuPdv)
        put("oznPosPr", oznPosPr)
        put("oznNapUr", oznNapUr)
        put("oznSlijed", oznSlijed.name)
        put("oibOper", oibOper)
        put("okolina", okolina.name)
        put("ignoreTls", ignoreTls)
        put("sljedeciBroj", sljedeciBroj)
        put("printerAddress", printerAddress)
        put("zadanaPdvStopa", zadanaPdvStopa)
        put("zadaniNacinPlac", zadaniNacinPlac.oznaka)
        put("zadanaJedMjere", zadanaJedMjere)
    }

    companion object {
        fun fromJson(o: JSONObject) = Tvrtka(
            id = o.optString("id", UUID.randomUUID().toString()),
            naziv = o.optString("naziv"),
            oib = o.optString("oib"),
            uSustavuPdv = o.optBoolean("uSustavuPdv"),
            oznPosPr = o.optString("oznPosPr", "POSL1"),
            oznNapUr = o.optString("oznNapUr", "1"),
            oznSlijed = runCatching { OznSlijed.valueOf(o.optString("oznSlijed", "P")) }.getOrDefault(OznSlijed.P),
            oibOper = o.optString("oibOper"),
            okolina = runCatching { FiskalOkolina.valueOf(o.optString("okolina", "TEST")) }.getOrDefault(FiskalOkolina.TEST),
            ignoreTls = o.optBoolean("ignoreTls"),
            sljedeciBroj = o.optLong("sljedeciBroj", 1L),
            printerAddress = o.optString("printerAddress"),
            zadanaPdvStopa = o.optString("zadanaPdvStopa", "25"),
            zadaniNacinPlac = NacinPlac.fromOznaka(o.optString("zadaniNacinPlac", "G")),
            zadanaJedMjere = o.optString("zadanaJedMjere", "kom"),
        )
    }
}
