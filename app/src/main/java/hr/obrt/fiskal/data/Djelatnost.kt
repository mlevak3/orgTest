package hr.obrt.fiskal.data

import hr.obrt.fiskal.model.OznSlijed
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Naplatni uređaj (blagajna) unutar poslovnog prostora. */
data class NaplatniUredaj(
    val id: String = UUID.randomUUID().toString(),
    var oznaka: String = "1",
    /** Sljedeći broj računa — koristi se kad je OznSlijed = N (po uređaju). */
    var sljedeciBroj: Long = 1L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("oznaka", oznaka); put("sljedeciBroj", sljedeciBroj)
    }

    companion object {
        fun fromJson(o: JSONObject) = NaplatniUredaj(
            id = o.optString("id", UUID.randomUUID().toString()),
            oznaka = o.optString("oznaka", "1"),
            sljedeciBroj = o.optLong("sljedeciBroj", 1L),
        )
    }
}

/** Poslovni prostor unutar djelatnosti, s popisom naplatnih uređaja. */
data class PoslovniProstor(
    val id: String = UUID.randomUUID().toString(),
    var oznaka: String = "POSL1",
    /** Sljedeći broj računa — koristi se kad je OznSlijed = P (po poslovnom prostoru). */
    var sljedeciBroj: Long = 1L,
    var naplatniUredjaji: MutableList<NaplatniUredaj> = mutableListOf(NaplatniUredaj()),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("oznaka", oznaka); put("sljedeciBroj", sljedeciBroj)
        put("naplatniUredjaji", JSONArray().apply { naplatniUredjaji.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): PoslovniProstor {
            val arr = o.optJSONArray("naplatniUredjaji") ?: JSONArray()
            val uredjaji = (0 until arr.length()).map { NaplatniUredaj.fromJson(arr.getJSONObject(it)) }.toMutableList()
            return PoslovniProstor(
                id = o.optString("id", UUID.randomUUID().toString()),
                oznaka = o.optString("oznaka", "POSL1"),
                sljedeciBroj = o.optLong("sljedeciBroj", 1L),
                naplatniUredjaji = uredjaji.ifEmpty { mutableListOf(NaplatniUredaj()) },
            )
        }
    }
}

/**
 * Djelatnost tvrtke — ima svoj popis poslovnih prostora (svaki sa svojim
 * naplatnim uređajima) te zadani prostor/uređaj koji se ponude pri izradi
 * novog računa.
 */
data class Djelatnost(
    val id: String = UUID.randomUUID().toString(),
    var naziv: String = "",
    var oznSlijed: OznSlijed = OznSlijed.P,
    var poslovniProstori: MutableList<PoslovniProstor> = mutableListOf(PoslovniProstor()),
    var zadaniPoslovniProstorId: String = "",
    var zadaniNaplatniUredjajId: String = "",
) {
    fun opis(): String = naziv.ifBlank { "Djelatnost" }

    fun zadaniProstor(): PoslovniProstor =
        poslovniProstori.firstOrNull { it.id == zadaniPoslovniProstorId } ?: poslovniProstori.first()

    fun zadaniUredjaj(prostor: PoslovniProstor = zadaniProstor()): NaplatniUredaj =
        prostor.naplatniUredjaji.firstOrNull { it.id == zadaniNaplatniUredjajId }
            ?: prostor.naplatniUredjaji.first()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("naziv", naziv)
        put("oznSlijed", oznSlijed.name)
        put("poslovniProstori", JSONArray().apply { poslovniProstori.forEach { put(it.toJson()) } })
        put("zadaniPoslovniProstorId", zadaniPoslovniProstorId)
        put("zadaniNaplatniUredjajId", zadaniNaplatniUredjajId)
    }

    companion object {
        fun fromJson(o: JSONObject): Djelatnost {
            val arr = o.optJSONArray("poslovniProstori") ?: JSONArray()
            val prostori = (0 until arr.length()).map { PoslovniProstor.fromJson(arr.getJSONObject(it)) }
                .toMutableList()
                .ifEmpty { mutableListOf(PoslovniProstor()) }
            return Djelatnost(
                id = o.optString("id", UUID.randomUUID().toString()),
                naziv = o.optString("naziv"),
                oznSlijed = runCatching { OznSlijed.valueOf(o.optString("oznSlijed", "P")) }.getOrDefault(OznSlijed.P),
                poslovniProstori = prostori,
                zadaniPoslovniProstorId = o.optString("zadaniPoslovniProstorId").ifBlank { prostori.first().id },
                zadaniNaplatniUredjajId = o.optString("zadaniNaplatniUredjajId").ifBlank { prostori.first().naplatniUredjaji.first().id },
            )
        }
    }
}
