package hr.obrt.fiskal

import hr.obrt.fiskal.fiskal.EscPosReceiptBuilder
import hr.obrt.fiskal.fiskal.QrCodeContent
import hr.obrt.fiskal.fiskal.ReceiptData
import hr.obrt.fiskal.model.NacinPlac
import hr.obrt.fiskal.model.OznSlijed
import hr.obrt.fiskal.model.Racun
import hr.obrt.fiskal.model.Stavka
import hr.obrt.fiskal.model.Zaglavlje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class EscPosReceiptBuilderTest {

    private val racun = Racun(
        zaglavlje = Zaglavlje("12345678901", true, "POSL1", "1", OznSlijed.P, "98765432109"),
        brOznRac = 7,
        datVrijeme = Date(0),
        stavke = listOf(
            Stavka(
                naziv = "Usluga č/š/ž test",
                kolicina = BigDecimal("2"),
                pdvStopa = BigDecimal("25"),
                neto = BigDecimal("100.00"),
                pdvIznos = BigDecimal("25.00"),
                ukupno = BigDecimal("125.00"),
            )
        ),
        nacinPlac = NacinPlac.G,
    )

    private val data = ReceiptData(
        naslovTvrtke = "Moj Obrt",
        racun = racun,
        jir = "12345678-1234-1234-1234-123456789012",
        zki = "abcdef0123456789abcdef0123456789",
        qrUrl = QrCodeContent.build("12345678-1234-1234-1234-123456789012", "zki", racun.datVrijeme, racun.iznosUkupno),
    )

    @Test
    fun sadrzi_init_i_cut_naredbe() {
        val b = EscPosReceiptBuilder.build(data)
        assertEquals(0x1B, b[0].toInt() and 0xFF)
        assertEquals(0x40, b[1].toInt() and 0xFF)
        // GS V 66 0 na kraju (djelomično odrezivanje)
        val zadnja4 = b.copyOfRange(b.size - 4, b.size).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x1D, 0x56, 66, 0), zadnja4)
    }

    @Test
    fun transliterira_dijakritike_i_ostaje_ascii() {
        val b = EscPosReceiptBuilder.build(data)
        val text = String(b, Charsets.US_ASCII)
        assertTrue("mora sadržavati transliterirani naziv", text.contains("Usluga c/s/z test"))
        assertTrue("ne smije sadržavati č/š/ž", !text.contains("č") && !text.contains("š") && !text.contains("ž"))
    }

    @Test
    fun sadrzi_jir_zki_i_ukupno() {
        val b = EscPosReceiptBuilder.build(data)
        val text = String(b, Charsets.US_ASCII)
        assertTrue(text.contains(data.jir!!))
        assertTrue(text.contains(data.zki))
        assertTrue(text.contains("125.00"))
    }

    @Test
    fun qr_zaglavlje_ima_ispravan_point_length() {
        val b = EscPosReceiptBuilder.build(data)
        val payloadLen = data.qrUrl.toByteArray(Charsets.UTF_8).size + 3
        // Zaglavlje "pohrani podatke": GS ( k pL pH 0x31 0x50 0x30
        val marker = byteArrayOf(0x31, 0x50, 0x30)
        val idx = indexOf(b, byteArrayOf(0x1D, 0x28, 0x6B)) // prvo pojavljivanje
        assertTrue(idx >= 0)
        // Pronađi blok koji sadrži marker 0x31 0x50 0x30 nakon pL pH
        var found = false
        var i = 0
        while (i < b.size - 8) {
            if (b[i] == 0x1D.toByte() && b[i + 1] == 0x28.toByte() && b[i + 2] == 0x6B.toByte()) {
                val pL = b[i + 3].toInt() and 0xFF
                val pH = b[i + 4].toInt() and 0xFF
                if (b[i + 5] == 0x31.toByte() && b[i + 6] == 0x50.toByte() && b[i + 7] == 0x30.toByte()) {
                    assertEquals(payloadLen, pL + (pH shl 8))
                    found = true
                }
            }
            i++
        }
        assertTrue("mora naći GS(k blok za pohranu QR podataka", found)
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
