package hr.obrt.fiskal.fiskal

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Ispis (ili spremanje u PDF) računa putem Android sustava ispisa.
 *
 * Račun se složi kao HTML (zaglavlje, stavke, ukupno, JIR, ZKI i QR kod) te
 * preda Android PrintManageru — korisnik može odabrati pisač ili „Spremi kao PDF".
 */
object ReceiptPrinter {

    // Zadržavamo referencu da WebView ne bude počišćen prije završetka ispisa.
    private var webViewRef: WebView? = null

    fun print(context: Context, data: ReceiptData) {
        val qrBitmap = QrRenderer.toBitmap(data.qrUrl, size = 480)
        val qrBase64 = QrRenderer.toBase64Png(qrBitmap)
        val html = buildHtml(data, qrBase64)

        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "Racun-${data.racun.brOznRac}-${data.racun.zaglavlje.oznPosPr}"
                val adapter = view.createPrintDocumentAdapter(jobName)
                printManager.print(
                    jobName,
                    adapter,
                    PrintAttributes.Builder().build(),
                )
                webViewRef = null
            }
        }
        webViewRef = webView
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun buildHtml(data: ReceiptData, qrBase64: String): String {
        val r = data.racun
        val z = r.zaglavlje
        val datum = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).format(r.datVrijeme)
        val brojRacuna = "${r.brOznRac}/${z.oznPosPr}/${z.oznNapUr}"

        val stavkeRedovi = r.stavke.joinToString("") { s ->
            "<tr><td>${esc(s.naziv)}</td>" +
                "<td class='r'>${s.kolicina.toPlainString()}</td>" +
                "<td class='r'>${FiskalFormat.amount(s.jedinicnaCijena)}</td>" +
                "<td class='r'>${FiskalFormat.amount(s.ukupno)}</td></tr>"
        }

        val pdvBlok = if (z.uSustavuPdv) {
            val redovi = r.pdvGrupe().joinToString("") { g ->
                "<tr><td>PDV ${FiskalFormat.amount(g.stopa)}%</td>" +
                    "<td class='r'>${FiskalFormat.amount(g.osnovica)}</td>" +
                    "<td class='r'>${FiskalFormat.amount(g.iznos)}</td></tr>"
            }
            "<table class='pdv'><tr><th>Porez</th><th class='r'>Osnovica</th><th class='r'>Iznos</th></tr>$redovi</table>"
        } else {
            "<p class='note'>Obveznik nije u sustavu PDV-a.</p>"
        }

        val jirRedak = data.jir?.let { "<div><b>JIR:</b> ${esc(it)}</div>" }
            ?: "<div class='warn'><b>JIR:</b> nije dodijeljen (naknadna dostava)</div>"

        return """
            <!DOCTYPE html><html lang="hr"><head><meta charset="UTF-8">
            <style>
              @page { margin: 10mm; }
              body { font-family: monospace; font-size: 12px; color: #000; max-width: 360px; }
              h1 { font-size: 16px; text-align: center; margin: 0 0 4px; }
              table { width: 100%; border-collapse: collapse; margin: 6px 0; }
              th, td { padding: 2px 0; text-align: left; }
              .r { text-align: right; }
              .line { border-top: 1px dashed #000; margin: 6px 0; }
              .total { font-size: 15px; font-weight: bold; text-align: right; margin: 6px 0; }
              .codes { word-break: break-all; font-size: 11px; }
              .qr { text-align: center; margin-top: 10px; }
              .qr img { width: 160px; height: 160px; }
              .note { font-size: 11px; }
              .warn { color: #b00; }
              .foot { text-align: center; font-size: 10px; margin-top: 8px; }
            </style></head><body>
              <h1>RAČUN</h1>
              <div style="text-align:center;font-weight:bold">${esc(data.naslovTvrtke)}</div>
              <div><b>OIB:</b> ${esc(z.oib)}</div>
              <div><b>Broj računa:</b> ${esc(brojRacuna)}</div>
              <div><b>Datum:</b> $datum</div>
              <div><b>Operater:</b> ${esc(z.oibOper)}</div>
              <div class="line"></div>
              <table>
                <tr><th>Naziv</th><th class="r">Kol.</th><th class="r">Cijena</th><th class="r">Iznos</th></tr>
                $stavkeRedovi
              </table>
              <div class="total">UKUPNO: ${FiskalFormat.amount(r.iznosUkupno)} EUR</div>
              $pdvBlok
              <div><b>Način plaćanja:</b> ${r.nacinPlac.opis}</div>
              <div class="line"></div>
              $jirRedak
              <div class="codes"><b>ZKI:</b> ${esc(data.zki)}</div>
              <div class="qr"><img src="data:image/png;base64,$qrBase64" alt="QR"></div>
              <div class="codes" style="font-size:9px;text-align:center"><b>QR poveznica:</b> ${esc(data.qrUrl)}</div>
              <div class="foot">Provjera računa: porezna.gov.hr/rn</div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
