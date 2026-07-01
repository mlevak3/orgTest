package hr.obrt.fiskal.data

import org.json.JSONObject
import java.util.UUID

/** Poslovni partner u šifrarniku tvrtke — predefinirani kupac za brzi odabir na računu. */
data class Partner(
    val id: String = UUID.randomUUID().toString(),
    var naziv: String = "",
    var oib: String = "",
    var adresa: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("naziv", naziv); put("oib", oib); put("adresa", adresa)
    }

    companion object {
        fun fromJson(o: JSONObject) = Partner(
            id = o.optString("id", UUID.randomUUID().toString()),
            naziv = o.optString("naziv"),
            oib = o.optString("oib"),
            adresa = o.optString("adresa"),
        )
    }
}
