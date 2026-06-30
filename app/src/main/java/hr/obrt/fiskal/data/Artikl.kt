package hr.obrt.fiskal.data

import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** Artikl/usluga u šifrarniku tvrtke. */
data class Artikl(
    val id: String = UUID.randomUUID().toString(),
    var naziv: String = "",
    var jedMjere: String = "kom",
    var jedCijena: BigDecimal = BigDecimal.ZERO,
    var pdvStopa: BigDecimal = BigDecimal("25"),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("naziv", naziv)
        put("jedMjere", jedMjere)
        put("jedCijena", jedCijena.toPlainString())
        put("pdvStopa", pdvStopa.toPlainString())
    }

    companion object {
        fun fromJson(o: JSONObject) = Artikl(
            id = o.optString("id", UUID.randomUUID().toString()),
            naziv = o.optString("naziv"),
            jedMjere = o.optString("jedMjere", "kom"),
            jedCijena = runCatching { BigDecimal(o.optString("jedCijena", "0")) }.getOrDefault(BigDecimal.ZERO),
            pdvStopa = runCatching { BigDecimal(o.optString("pdvStopa", "25")) }.getOrDefault(BigDecimal("25")),
        )
    }
}
