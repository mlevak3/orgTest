package hr.obrt.fiskal.data

import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import hr.obrt.fiskal.model.Zaglavlje
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

/** Spremljeni (fiskalizirani ili pokušani) račun, dovoljan za pregled, ispis i email. */
data class SavedInvoice(
    val id: String,
    val companyId: String,
    val naslovTvrtke: String,
    val racun: Racun,
    val jir: String?,
    val zki: String,
    val qrUrl: String,
    val status: String,
    val createdAt: Long,
) {
    fun brojRacuna(): String =
        "${racun.brOznRac}/${racun.zaglavlje.oznPosPr}/${racun.zaglavlje.oznNapUr}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("companyId", companyId)
        put("naslovTvrtke", naslovTvrtke)
        put("jir", jir ?: JSONObject.NULL)
        put("zki", zki)
        put("qrUrl", qrUrl)
        put("status", status)
        put("createdAt", createdAt)
        val z = racun.zaglavlje
        put("oib", z.oib)
        put("uSustavuPdv", z.uSustavuPdv)
        put("oznPosPr", z.oznPosPr)
        put("oznNapUr", z.oznNapUr)
        put("oznSlijed", z.oznSlijed.name)
        put("oibOper", z.oibOper)
        put("brOznRac", racun.brOznRac)
        put("datVrijeme", racun.datVrijeme.time)
        put("nacinPlac", racun.nacinPlac.oznaka)
        put("nakDost", racun.nakDost)
        val arr = JSONArray()
        racun.stavke.forEach { s ->
            arr.put(JSONObject().apply {
                put("naziv", s.naziv)
                put("kolicina", s.kolicina.toPlainString())
                put("pdvStopa", s.pdvStopa.toPlainString())
                put("neto", s.neto.toPlainString())
                put("pdvIznos", s.pdvIznos.toPlainString())
                put("ukupno", s.ukupno.toPlainString())
            })
        }
        put("stavke", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): SavedInvoice {
            val zaglavlje = Zaglavlje(
                oib = o.optString("oib"),
                uSustavuPdv = o.optBoolean("uSustavuPdv"),
                oznPosPr = o.optString("oznPosPr", "POSL1"),
                oznNapUr = o.optString("oznNapUr", "1"),
                oznSlijed = runCatching { OznSlijed.valueOf(o.optString("oznSlijed", "P")) }.getOrDefault(OznSlijed.P),
                oibOper = o.optString("oibOper"),
            )
            val stavkeArr = o.optJSONArray("stavke") ?: JSONArray()
            val stavke = (0 until stavkeArr.length()).map { i ->
                val s = stavkeArr.getJSONObject(i)
                Stavka(
                    naziv = s.optString("naziv"),
                    kolicina = BigDecimal(s.optString("kolicina", "0")),
                    pdvStopa = BigDecimal(s.optString("pdvStopa", "0")),
                    neto = BigDecimal(s.optString("neto", "0")),
                    pdvIznos = BigDecimal(s.optString("pdvIznos", "0")),
                    ukupno = BigDecimal(s.optString("ukupno", "0")),
                )
            }
            val racun = Racun(
                zaglavlje = zaglavlje,
                brOznRac = o.optLong("brOznRac", 0),
                datVrijeme = Date(o.optLong("datVrijeme", System.currentTimeMillis())),
                stavke = stavke,
                nacinPlac = NacinPlac.fromOznaka(o.optString("nacinPlac", "G")),
                nakDost = o.optBoolean("nakDost", false),
            )
            return SavedInvoice(
                id = o.optString("id", UUID.randomUUID().toString()),
                companyId = o.optString("companyId"),
                naslovTvrtke = o.optString("naslovTvrtke"),
                racun = racun,
                jir = if (o.isNull("jir")) null else o.optString("jir"),
                zki = o.optString("zki"),
                qrUrl = o.optString("qrUrl"),
                status = o.optString("status"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}
