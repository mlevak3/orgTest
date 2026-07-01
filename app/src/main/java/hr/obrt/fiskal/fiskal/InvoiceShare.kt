package hr.obrt.fiskal.fiskal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/** Slanje/dijeljenje računa kao PDF privitak (preko sustava dijeljenja). */
object InvoiceShare {

    private fun pdfUri(context: Context, data: ReceiptData) = FileProvider.getUriForFile(
        context, context.packageName + ".fileprovider", ReceiptPdf.render(context, data),
    )

    private fun poruka(data: ReceiptData) =
        "Račun ${data.brojRacuna()}.\nJIR: ${data.jir ?: "-"}\nZKI: ${data.zki}\nProvjera: ${data.qrUrl}"

    /** Email (chooser filtriran na e-mail aplikacije naslovom, ali prikazuje i ostale). */
    fun emailPdf(context: Context, data: ReceiptData) =
        share(context, data, "Pošalji račun emailom", "Račun ${data.brojRacuna()}")

    /** Generičko dijeljenje (WhatsApp, Viber, Drive, Bluetooth…). */
    fun sharePdf(context: Context, data: ReceiptData) =
        share(context, data, "Podijeli račun", null)

    private fun share(context: Context, data: ReceiptData, naslovChoosera: String, subject: String?) {
        val uri = pdfUri(context, data)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            putExtra(Intent.EXTRA_TEXT, "U privitku je ${poruka(data)}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, naslovChoosera).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Kopira tekst (JIR ili ZKI) u međuspremnik. */
    fun copyToClipboard(context: Context, label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
    }
}
