package hr.obrt.fiskal.data

import android.content.Context

/** Ručno odabrana tema aplikacije (neovisno o pojedinoj tvrtki). */
enum class TemaAplikacije(val opis: String) {
    SUSTAV("Prati sustav"),
    SVIJETLA("Svijetla"),
    TAMNA("Tamna");

    fun sljedeca(): TemaAplikacije = entries[(ordinal + 1) % entries.size]
}

/** Jednostavne, nešifrirane postavke aplikacije (bez osjetljivih podataka). */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var tema: TemaAplikacije
        get() = runCatching { TemaAplikacije.valueOf(prefs.getString(K_TEMA, null) ?: "") }.getOrDefault(TemaAplikacije.SUSTAV)
        set(v) = prefs.edit().putString(K_TEMA, v.name).apply()

    companion object {
        private const val K_TEMA = "tema"
    }
}
