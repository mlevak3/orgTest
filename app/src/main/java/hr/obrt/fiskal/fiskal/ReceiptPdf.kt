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

        val pdvBroj = if (z.uSustavuPdv) r.pdvGrupe().size + 1 else 1
        val brojLinija = 6 + 1 + 1 + r.stavke.size + 1 + pdvBroj + 1 + 1 + 1 + 1
        val height = (margin * 2 + brojLinija * lineH + 12 + qrSize + 24).toInt()

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

        val tW = title.measureText("RAČUN")
        c.drawText("RAČUN", (pageWidth - tW) / 2f, y, title); y += lineH + 4f
        line(data.naslovTvrtke, p = bold)
        line("OIB: ${z.oib}")
        line("Račun: ${data.brojRacuna()}")
        line("Datum: $datum")
        line("Operater: ${z.oibOper}")
        sep()
        line("Stavka", "Iznos", bold)
        r.stavke.forEach { s ->
            val naziv = "${s.naziv} (${s.kolicina.toPlainString()}×${FiskalFormat.amount(s.jedinicnaCijena)})"
            line(naziv.take(34), FiskalFormat.amount(s.ukupno))
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
        sep()
        line("JIR: ${data.jir ?: "nije dodijeljen (naknadna dostava)"}")
        line("ZKI: ${data.zki}")

        val qr = QrRenderer.toBitmap(data.qrUrl, qrSize)
        c.drawBitmap(Bitmap.createScaledBitmap(qr, qrSize, qrSize, false), (pageWidth - qrSize) / 2f, y + 4f, null)

        doc.finishPage(page)

        val file = File(context.cacheDir, "racun_${r.brOznRac}_${r.zaglavlje.oznPosPr}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
