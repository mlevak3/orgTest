package hr.obrt.fiskal.fiskal

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/** Slanje računa emailom kao PDF privitak (preko sustava dijeljenja). */
object InvoiceShare {

    fun emailPdf(context: Context, data: ReceiptData) {
        val file = ReceiptPdf.render(context, data)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Račun ${data.brojRacuna()}")
            putExtra(
                Intent.EXTRA_TEXT,
                "U privitku je račun ${data.brojRacuna()}.\n" +
                    "JIR: ${data.jir ?: "-"}\nZKI: ${data.zki}\n" +
                    "Provjera: ${data.qrUrl}",
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Pošalji račun emailom")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
