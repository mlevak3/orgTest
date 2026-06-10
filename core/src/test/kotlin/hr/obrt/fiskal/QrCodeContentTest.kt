package hr.obrt.fiskal

import hr.obrt.fiskal.fiskal.FiskalFormat
import hr.obrt.fiskal.fiskal.QrCodeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Date

class QrCodeContentTest {

    private val date = Date(0)

    @Test
    fun koristi_jir_kad_postoji() {
        val url = QrCodeContent.build("12345678-1234-1234-1234-123456789012", "zki32", date, BigDecimal("125.00"))
        val expected = "https://porezna.gov.hr/rn?jir=12345678-1234-1234-1234-123456789012" +
            "&datv=${FiskalFormat.qrDateTime(date)}&izn=12500"
        assertEquals(expected, url)
    }

    @Test
    fun koristi_zki_kad_jir_nedostaje() {
        val url = QrCodeContent.build(null, "abcdef0123456789abcdef0123456789", date, BigDecimal("9.99"))
        assertTrue(url.contains("?zki=abcdef0123456789abcdef0123456789&"))
        assertTrue(url.contains("&izn=999"))
        assertTrue(!url.contains("jir="))
    }

    @Test
    fun iznos_je_u_centima_kao_cijeli_broj() {
        assertEquals("12500", FiskalFormat.amountInCents(BigDecimal("125.00")))
        assertEquals("123450", FiskalFormat.amountInCents(BigDecimal("1234.50")))
        assertEquals("5", FiskalFormat.amountInCents(BigDecimal("0.05")))
    }
}
