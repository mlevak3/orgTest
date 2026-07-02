package hr.obrt.fiskal.fiskal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Generira PDF računa (za email/spremanje) crtanjem na Canvas — bez vanjskih ovisnosti. */
object ReceiptPdf {

    fun render(context: Context, data: ReceiptData): File {
        val r = data.racun
        val z = r.zaglavlje
        val datum = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(r.datVrijeme)

        val pageWidth = 380
        val margin = 20f
        val lineH = 16f
        val qrSize = 150

        val title = Paint().apply { textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val bold = Paint().apply { textSize = 11f; isFakeBoldText = true; typeface = Typeface.MONOSPACE; isAntiAlias = true }
        val normal = Paint().apply { textSize = 11f; typeface = Typeface.MONOSPACE; isAntiAlias = true }
        val tiny = Paint().apply { textSize = 8f; typeface = Typeface.MONOSPACE; isAntiAlias = true }

        val tinyH = 11f
        val adresaLines = data.adresaTvrtke.takeIf { it.isNotBlank() }?.split('\n') ?: emptyList()
        val urlLines = wrap(data.qrUrl, tiny, pageWidth - 2 * margin)
        val kupacText = if (data.kupac.isNotBlank() || data.kupacOib.isNotBlank())
            "Kupac: ${data.kupac}" + (if (data.kupacOib.isNotBlank()) " (OIB ${data.kupacOib})" else "") else null
        val kupacAdresaText = data.kupacAdresa.takeIf { it.isNotBlank() }
        val napomenaLines = if (data.napomena.isNotBlank())
            wrap("Napomena: ${data.napomena}", normal, pageWidth - 2 * margin) else emptyList()
        val pdvBroj = if (z.uSustavuPdv) r.pdvGrupe().size + 1 else 1
        val dodatne = (if (kupacText != null) 1 else 0) + (if (kupacAdresaText != null) 1 else 0) + napomenaLines.size + adresaLines.size
        val brojLinija = 6 + 1 + 1 + r.stavke.size + 1 + pdvBroj + 1 + 1 + 1 + 1 + dodatne
        // Svaka stavka dobiva dodatni redak "kol. x cijena"; PDV obveznik još i redak neto/PDV.
        val podredciPoStavci = if (z.uSustavuPdv) 2 else 1
        val pdvPodredci = r.stavke.size * podredciPoStavci

        val logoMaxW = 140f; val logoMaxH = 60f
        val logoBitmap = data.logoPng?.let { png ->
            runCatching { android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) }.getOrNull()
        }
        val logoDrawH = logoBitmap?.let { bmp ->
            val skala = minOf(logoMaxW / bmp.width, logoMaxH / bmp.height, 1f)
            bmp.height * skala
        } ?: 0f

        val height = (margin * 2 + brojLinija * lineH + pdvPodredci * tinyH + 12 + qrSize + 8 +
            (1 + urlLines.size) * tinyH + 16 + logoDrawH + (if (logoBitmap != null) 8 else 0)).toInt()

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, height, 1).create())
        val c = page.canvas

        var y = margin + 14f
        fun line(left: String, right: String? = null, p: Paint = normal) {
            c.drawText(left, margin, y, p)
            if (right != null) {
                val w = p.measureText(right)
                c.drawText(right, pageWidth - margin - w, y, p)
            }
            y += lineH
        }
        fun sep() {
            c.drawLine(margin, y - 6f, pageWidth - margin, y - 6f, normal)
            y += lineH
        }

        if (logoBitmap != null) {
            val skala = minOf(logoMaxW / logoBitmap.width, logoMaxH / logoBitmap.height, 1f)
            val w = (logoBitmap.width * skala).toInt().coerceAtLeast(1)
            val h = (logoBitmap.height * skala).toInt().coerceAtLeast(1)
            c.drawBitmap(Bitmap.createScaledBitmap(logoBitmap, w, h, true), (pageWidth - w) / 2f, y, null)
            y += h + 8f
        }

        val tW = title.measureText("RAČUN")
        c.drawText("RAČUN", (pageWidth - tW) / 2f, y, title); y += lineH + 4f
        line(data.naslovTvrtke, p = bold)
        adresaLines.forEach { line(it) }
        line("OIB: ${z.oib}")
        line("Račun: ${data.brojRacuna()}")
        line("Datum: $datum")
        line("Operater: ${z.oibOper}")
        kupacText?.let { line(it) }
        kupacAdresaText?.let { line(it) }
        sep()
        line("Stavka", "Ukupno", bold)
        r.stavke.forEach { s ->
            line(s.naziv.take(34), FiskalFormat.amount(s.ukupno))
            c.drawText(
                "  ${s.kolicina.toPlainString()} ${s.jedMjere} x ${FiskalFormat.amount(s.jedinicnaCijena())}",
                margin, y, tiny,
            )
            y += tinyH
            if (z.uSustavuPdv) {
                c.drawText(
                    "  neto ${FiskalFormat.amount(s.neto)} · PDV ${FiskalFormat.amount(s.pdvStopa)}% = ${FiskalFormat.amount(s.pdvIznos)}",
                    margin, y, tiny,
                )
                y += tinyH
            }
        }
        line("UKUPNO (EUR):", FiskalFormat.amount(r.iznosUkupno), bold)
        if (z.uSustavuPdv) {
            r.pdvGrupe().forEach { g ->
                line("PDV ${FiskalFormat.amount(g.stopa)}% (osn. ${FiskalFormat.amount(g.osnovica)})", FiskalFormat.amount(g.iznos))
            }
        } else {
            line("Obveznik nije u sustavu PDV-a.")
        }
        line("Plaćanje: ${r.nacinPlac.opis}")
        napomenaLines.forEach { line(it) }
        sep()
        line("JIR: ${data.jir ?: "nije dodijeljen (naknadna dostava)"}")
        line("ZKI: ${data.zki}")

        val qr = QrRenderer.toBitmap(data.qrUrl, qrSize)
        c.drawBitmap(Bitmap.createScaledBitmap(qr, qrSize, qrSize, false), (pageWidth - qrSize) / 2f, y + 4f, null)
        y += qrSize + 12f

        c.drawText("QR poveznica (provjera računa):", margin, y, tiny); y += tinyH
        urlLines.forEach { c.drawText(it, margin, y, tiny); y += tinyH }

        doc.finishPage(page)

        val file = File(context.cacheDir, "racun_${r.brOznRac}_${r.zaglavlje.oznPosPr}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Prelama tekst u retke koji stanu u zadanu širinu. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = start + 1
            while (end < text.length && paint.measureText(text.substring(start, end + 1)) <= maxWidth) {
                end++
            }
            lines.add(text.substring(start, end))
            start = end
        }
        return lines
    }
}
